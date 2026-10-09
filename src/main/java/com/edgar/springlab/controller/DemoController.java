package com.edgar.springlab.controller;

import com.edgar.springlab.service.MessageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
/**
 * @RestController = @Controller + @ResponseBody
 *
 * Controller负责标识Spring MVC控制器
 * ResponseBody负责声明方法的返回值写入HTTP响应体，而不是按照默认方式进行视图解析
 */
public class DemoController {

    private final MessageService messageService;

    private final ApplicationContext applicationContext;

    public DemoController(
            @Qualifier("chineseMessageService") MessageService messageService,
            ApplicationContext applicationContext
    ){
        this.messageService = messageService;
        this.applicationContext = applicationContext;
    }


    @GetMapping("/hello")
    public String hello(){
        return messageService.getMessage();
    }


    @GetMapping("/singleton")
    public boolean testSingleton(){
        Object bean1 = applicationContext.getBean("chineseMessageService");

        Object bean2 = applicationContext.getBean("chineseMessageService");

        return bean1 == bean2;
    }
}
