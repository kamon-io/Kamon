package kamon.annotation

import org.springframework.boot.autoconfigure._
import org.springframework.stereotype._
import org.springframework.web.bind.annotation._

@Controller
@EnableAutoConfiguration
@RequestMapping(Array("/kamon"))
@EnableKamon
class KamonController {

    @RequestMapping(Array("/counter"))
    @ResponseBody
    @Count(name = "awesomeCounter")
    def counter(): String =  "count!!!"
}