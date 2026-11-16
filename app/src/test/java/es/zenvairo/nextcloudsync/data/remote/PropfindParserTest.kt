package es.zenvairo.nextcloudsync.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class PropfindParserTest {

    private val parser = PropfindParser()

    private val sampleResponse = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
          <d:response>
            <d:href>/remote.php/dav/files/alice/Photos/</d:href>
            <d:propstat>
              <d:prop>
                <d:getetag>"root-etag"</d:getetag>
                <d:resourcetype><d:collection/></d:resourcetype>
                <oc:fileid>100</oc:fileid>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/files/alice/Photos/cat.jpg</d:href>
            <d:propstat>
              <d:prop>
                <d:getetag>"cat-etag"</d:getetag>
                <d:getcontentlength>2048</d:getcontentlength>
                <d:getlastmodified>Mon, 06 Jan 2025 10:00:00 GMT</d:getlastmodified>
                <oc:fileid>101</oc:fileid>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `parses files and collections, skipping the propfind target itself`() {
        val entries = parser.parse(
            ByteArrayInputStream(sampleResponse.toByteArray()),
            "/remote.php/dav/files/alice",
            queriedPath = "/Photos"
        )

        assertThat(entries).hasSize(1)
        val cat = entries.single()
        assertThat(cat.path).isEqualTo("Photos/cat.jpg")
        assertThat(cat.isDirectory).isFalse()
        assertThat(cat.etag).isEqualTo("cat-etag")
        assertThat(cat.size).isEqualTo(2048)
        assertThat(cat.fileId).isEqualTo("101")
    }

    /**
     * The shape Nextcloud actually returns: a 200 propstat with the properties it has, plus a 404
     * one naming those it doesn't (getcontentlength is meaningless on a collection). Reading the
     * 404 as the response's status dropped every folder from every listing.
     */
    @Test
    fun `keeps entries whose response also carries a 404 propstat for missing properties`() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/alice/Talk/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getetag>&quot;talk-etag&quot;</d:getetag>
                    <d:resourcetype><d:collection/></d:resourcetype>
                    <oc:fileid>1130237</oc:fileid>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop><d:getcontentlength/></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = parser.parse(ByteArrayInputStream(xml.toByteArray()), "/remote.php/dav/files/alice")

        assertThat(entries).hasSize(1)
        assertThat(entries.single().path).isEqualTo("Talk")
        assertThat(entries.single().isDirectory).isTrue()
        assertThat(entries.single().etag).isEqualTo("talk-etag")
    }

    @Test
    fun `without the queried path only the account root self-entry is skipped`() {
        val entries = parser.parse(ByteArrayInputStream(sampleResponse.toByteArray()), "/remote.php/dav/files/alice")

        // Photos/ itself is now kept, since it no longer matches the (empty) queried path.
        assertThat(entries.map { it.path }).containsExactly("Photos", "Photos/cat.jpg")
    }
}
