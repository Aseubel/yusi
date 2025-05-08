package com.aseubel.yusi;

import com.aseubel.yusi.common.disruptor.DisruptorProducer;
import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class YusiApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Resource
    private DisruptorProducer disruptorProducer;

    @Test
    void jpaTest() {
        User user = new User();
        user.setUserName("test");
        user.setPassword("password");
        user.generateUserId();
        userRepository.save(user);
        System.out.println(userRepository.findAll());
        System.out.println(userRepository.findById(user.getId()).orElseThrow());
    }

    @Test
    void disruptorTest() {
        Diary diary = new Diary();
        diary.setUserId("test");
        diary.setTitle("test");
        diary.setContent("test");
        diary.generateId();
        disruptorProducer.publish(diary, EventType.DIARY_WRITE);
    }

}
