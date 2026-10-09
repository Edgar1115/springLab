package com.edgar.springlab.service.impl;

import com.edgar.springlab.service.MessageService;
import org.springframework.stereotype.Service;

@Service("englishMessageService")
public class EnglishMessageService implements MessageService {

    @Override
    public String getMessage() {
        return "Hello, Spring!";
    }
}
