import com.github.onlaait.dcapi.dccon.DcconInfo
import kotlin.test.Test

class DcconInfoTest {

    val packageId = 168838
    val code = "62b5df2be09d3ca567b1c5bc12d46b394aa3b1058c6e4d0ca41648b65fe8266eadc328cfdea5b74810026b505984e855619060870d4d06195f505e32f80e07e9b799d64465cc69d40680a23a03"
    val loginSession by lazy { Secret.loginSession }

    @Test
    fun packageId() {
        DcconInfo().getFromPackageId(packageId)
            .also { println(it);require(it != null) }
    }

    @Test
    fun code() {
        DcconInfo().getFromCode(code)
            .also { println(it);println(it!!.pkg.list.first { it.code == code }) }
    }

    @Test
    fun `login packageId`() {
        DcconInfo(loginSession).getFromPackageId(packageId)
            .also { println(it);require(it != null) }
    }

    @Test
    fun `login code`() {
        DcconInfo(loginSession).getFromCode(code)
            .also { println(it);println(it!!.pkg.list.first { it.code == code }) }
    }
}