package com.edgar.springlab.service.impl;

import com.edgar.springlab.service.MessageService;
import org.springframework.stereotype.Service;

@Service("chineseMessageService")
public class ChineseMessageService implements MessageService {

    @Override
    public String getMessage(){
        return "你好, Spring!";
    }
}
