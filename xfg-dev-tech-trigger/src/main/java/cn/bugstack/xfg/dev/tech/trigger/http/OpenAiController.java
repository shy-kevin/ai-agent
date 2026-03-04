package cn.bugstack.xfg.dev.tech.trigger.http;

import cn.bugstack.xfg.dev.tech.api.IAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/openai/")
@RequiredArgsConstructor
@Tag(name = "OpenAi AI 对话管理", description = "AI 对话相关接口") // OpenAPI 3.0 注解
public class OpenAiController implements IAiService {

    private final OpenAiChatClient openAiChatClient;

    private final PgVectorStore pgVectorStore;


    @Override
    @GetMapping("generate")
    @Operation(summary = "生成对话响应", description = "根据模型和消息内容生成 AI 对话响应")
    public ChatResponse generate(
            @Parameter(description = "AI 模型名称 (如：deepseek-r1:1.5b)", required = true) @RequestParam String model,
            @Parameter(description = "用户输入的消息内容", required = true) @RequestParam String message) {
        log.info("ollama generate model: {}, message: {}", model, message);
        return openAiChatClient.call(new Prompt(message, OpenAiChatOptions.builder().withModel( model).build()));
    }

    @Override
    @RequestMapping(value = "generate_stream",method = {RequestMethod.GET})
    @Operation(summary = "流式生成对话响应", description = "根据模型和消息内容流式生成 AI 对话响应，适合长文本输出")
    public Flux<ChatResponse> generateStream(
            @Parameter(description = "AI 模型名称 (如：deepseek-r1:1.5b)", required = true) @RequestParam String model,
            @Parameter(description = "用户输入的消息内容", required = true) @RequestParam String message) {
        log.info("ollama generate_stream model: {}, message: {}", model, message);
        return openAiChatClient.stream(new Prompt(message, OpenAiChatOptions.builder().withModel( model).build()));
    }

    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    @Operation(summary = "rag知识库流式生成对话响应", description = "根据模型和消息内容流式生成 AI 对话响应，适合长文本输出")
    @Override
    public Flux<ChatResponse> generateStreamRag(@RequestParam String model, @RequestParam String ragTag, @RequestParam String message){
        log.info("openai generate_stream_rag model: {}, ragTag: {}, message: {}", model, ragTag, message);
        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        SearchRequest searchRequest = SearchRequest.query( message).withFilterExpression("knowledge == '"+ragTag+"'");
        List<Document> documents = pgVectorStore.similaritySearch(searchRequest);
        log.info("搜索到 {} 个文档片段", documents.size());
        String collect = documents.stream().map(Document::getContent).collect(Collectors.joining());

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents",collect));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage( message));
        messages.add(ragMessage);
        log.info("messages: {}", messages);

        return openAiChatClient.stream(new Prompt(messages, OpenAiChatOptions.builder().withModel( model).build()));
    }

}
