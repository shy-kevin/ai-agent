package cn.bugstack.xfg.dev.tech.trigger.http;

import cn.bugstack.xfg.dev.tech.api.IAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama")
@RequiredArgsConstructor
public class OllamaController implements IAiService {

    private final OllamaChatClient ollamaClient;

    @Override
    @GetMapping("/generate")
    public ChatResponse generate(@RequestParam String model,@RequestParam String message) {
        log.info("ollama generate model: {}, message: {}", model, message);
        return ollamaClient.call(new Prompt(message, OllamaOptions.create().withModel( model)));
    }

    @Override
    @RequestMapping(value = "/generate_stream",method = {RequestMethod.GET})
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        log.info("ollama generate_stream model: {}, message: {}", model, message);
        return ollamaClient.stream(new Prompt(message, OllamaOptions.create().withModel( model)));
    }
}
