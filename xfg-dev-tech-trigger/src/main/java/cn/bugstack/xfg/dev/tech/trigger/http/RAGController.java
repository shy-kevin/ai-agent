package cn.bugstack.xfg.dev.tech.trigger.http;

import cn.bugstack.xfg.dev.tech.api.IRAGService;
import cn.bugstack.xfg.dev.tech.api.Response.Response;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
@RequiredArgsConstructor
public class RAGController implements IRAGService {
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private PgVectorStore pgVectorStore;
    @Resource
    private RedissonClient redissonClient;

    // 查询知识库标签
    @Override
    @GetMapping("query_rag_tag_list")
    public Response<List<String>> queryRagTagList() {
        RList<String> ragTag = redissonClient.getList("ragTag");
        log.info("所有ragTag: {}", ragTag);
        return Response.<List<String>>builder().code("0000").info("调用成功").data(ragTag).build();
    }

    // 上传文件
    @Override
    @PostMapping(value = "file/upload",headers = "Content-Type=multipart/form-data")
    public Response<String> uploadFile(@RequestParam String ragTag,@RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库{}", ragTag);
        for (MultipartFile file : files) {
            TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = tikaDocumentReader.get();
            List<Document> splits = tokenTextSplitter.apply(documents);

            //添加知识库标签
            documents.forEach(document -> document.getMetadata().put("knowledge", ragTag));
            splits.forEach(document -> document.getMetadata().put("knowledge", ragTag));
            pgVectorStore.accept(splits);

            // 添加知识库记录
            RList<Object> ragTag1 = redissonClient.getList("ragTag");
            if(!ragTag1.contains(ragTag)){
                ragTag1.add(ragTag);
            }
        }
        log.info("上传知识库成功{}", ragTag);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }
}
