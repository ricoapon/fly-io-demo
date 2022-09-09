package nl.ricoapon.flyiodemo;

import nl.ricoapon.flyiodemo.database.Message;
import nl.ricoapon.flyiodemo.database.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
public class MessageController {
    @Autowired
    private MessageRepository messageRepository;

    @GetMapping("/")
    public Iterable<Message> allMessages() {
        return messageRepository.findAll();
    }

    @GetMapping("/add-message")
    public String addMessage() {
        Message message = new Message();
        message.setContent("New content generated at " + new Date());
        messageRepository.save(message);
        return "Succeeded";
    }
}
