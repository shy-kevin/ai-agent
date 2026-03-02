package cn.bugstack.xfg.dev.tech;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaClient;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void testUpload() {
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader("./data/file.txt");

        List<Document> documents = tikaDocumentReader.get();
        // 查看实际返回了多少个Document
        log.info("读取到 {} 个文档片段", documents.size());
        List<Document> splits = tokenTextSplitter.apply(documents);

        documents.forEach(document -> document.getMetadata().put("knowledge","测试知识库"));
        splits.forEach(document -> document.getMetadata().put("knowledge","测试知识库"));

        pgVectorStore.add(splits);
        log.info("上传成功");

    }

    @Test
    public void chat(){
        String message = "王大瓜哪一年出生";
        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;


        SearchRequest searchRequest = SearchRequest.query( message).withTopK(5).withFilterExpression("knowledge == '测试知识库'");
        List<Document> documents = pgVectorStore.similaritySearch(searchRequest);
        log.info("搜索到 {} 个文档片段", documents.size());
        String collect = documents.stream().map(Document::getContent).collect(Collectors.joining());

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents",collect));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage( message));
        messages.add(ragMessage);

        ChatResponse chatResponse = ollamaClient.call(new Prompt( messages, OllamaOptions.create().withModel("deepseek-r1:1.5b")));

        log.info("结果：{}", JSON.toJSONString(chatResponse));
    }

}
