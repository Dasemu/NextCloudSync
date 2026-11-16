package es.zenvairo.nextcloudsync.data.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class LoginFlowV2ClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: LoginFlowV2Client

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = LoginFlowV2Client(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `initiate parses the login and poll urls`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"poll":{"token":"tok123","endpoint":"${server.url("/login/v2/poll")}"},"login":"${server.url("/login/v2/flow")}"}"""
            )
        )

        val result = client.initiate(server.url("/").toString())

        assertThat(result.poll.token).isEqualTo("tok123")
        assertThat(result.login).contains("/login/v2/flow")
    }

    @Test
    fun `poll returns Pending on 404 while the user hasn't finished in the browser`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.poll(PollInfo("tok123", server.url("/poll").toString()))
        assertThat(result).isInstanceOf(LoginPollResult.Pending::class.java)
    }

    @Test
    fun `poll returns Success once the server has credentials`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"server":"https://cloud.example.com","loginName":"alice","appPassword":"secret"}""")
        )
        val result = client.poll(PollInfo("tok123", server.url("/poll").toString()))
        assertThat(result).isInstanceOf(LoginPollResult.Success::class.java)
        val success = result as LoginPollResult.Success
        assertThat(success.credentials.loginName).isEqualTo("alice")
        assertThat(success.credentials.appPassword).isEqualTo("secret")
    }
}
