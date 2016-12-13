package kamon.annotation;

import org.springframework.boot.autoconfigure.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;

@Controller
@EnableAutoConfiguration
@RequestMapping("/kamon")
@EnableKamon
public class KamonController {

    @RequestMapping("/counter")
    @ResponseBody
    @Count(name = "awesomeCounter")
    public String counter() {  return "count!!!"; }
}
