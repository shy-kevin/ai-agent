package cn.bugstack.xfg.dev.tech.trigger.http;

import cn.bugstack.xfg.dev.tech.api.IRAGService;
import cn.bugstack.xfg.dev.tech.api.Response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
@RequiredArgsConstructor
@Tag(name = "RAG 知识库管理", description = "知识库相关接口") // OpenAPI 3.0 注解
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
    @Operation(summary = "查询知识库标签列表", description = "获取所有已上传的知识库标签")
    public Response<List<String>> queryRagTagList() {
        RList<String> ragTag = redissonClient.getList("ragTag");
        log.info("所有 ragTag: {}", ragTag);
        return Response.<List<String>>builder().code("0000").info("调用成功").data(ragTag).build();
    }

    // 上传文件
    @Override
    @PostMapping(value = "file/upload",headers = "Content-Type=multipart/form-data")
    @Operation(summary = "上传知识库文件", description = "上传文件到指定知识库，支持多文件上传")
    public Response<String> uploadFile(
            @Parameter(description = "知识库标签名称", required = true) @RequestParam String ragTag,
            @Parameter(description = "待上传的文件列表", required = true) @RequestParam("file") List<MultipartFile> files) {
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

    @Override
    @Operation(summary = "分析git仓库", description = "分析git仓库，并把代码和文档添加到知识库")
    @PostMapping("analyze_git_repository")
    public Response<String> analyzeGitRepository(
            @Parameter(description = "git仓库地址", required = true) @RequestParam String repoUrl,
            @Parameter(description = "git仓库用户名", required = true) @RequestParam String userName,
            @Parameter(description = "git仓库密码", required = true) @RequestParam String token,
            @Parameter(description = "git仓库分支", required = true) @RequestParam String branch) throws Exception {
        // 配置 HTTP 和 HTTPS 代理 (常用的代理地址和端口)，不然 github 访问不了
        // GitHub Copilot、Clash: 7890
        // 其他代理工具：通常是 7890, 8080, 10808 等
        // TODO: 如果代理地址变化，请修改 application-dev.yml 中的 spring.http.proxy 配置
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "7890");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "7890");

        // 把git代码克隆到本地clone-repo/
        String localPath = "clone-repo";
        String repoProjectName = exportRepoProjectName(repoUrl);

        log.info("克隆路径:"+new File(localPath).getAbsolutePath());
        FileUtils.deleteDirectory(new File(localPath));

        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                .setBranch(branch)
                .call();
        git.close();
        log.info("克隆成功");

        // 遍历 clone-repo/ 目录下的所有文件,把文件内容添加到知识库
        Files.walkFileTree(Paths.get("clone-repo"), new SimpleFileVisitor<>() {

            // 跳过 .git 目录
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String dirName = dir.toString();
                if (dirName.contains(".git") || dirName.contains("node_modules") || dirName.contains("target")) {
                    log.info("跳过目录：{}", dirName);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.toString().toLowerCase();

                // 只处理常见代码和文档文件
                if (!isCodeOrDocFile(fileName)) {
                    log.debug("跳过非代码文件：{}", fileName);
                    return FileVisitResult.CONTINUE;
                }

                log.info("处理文件：{}", file.toString());

                try {
                    PathResource resource = new PathResource(file);
                    TikaDocumentReader reader = new TikaDocumentReader(resource);

                    List<Document> documents = reader.get();

                    // 检查文档内容是否为空
                    if (documents == null || documents.isEmpty()) {
                        log.warn("文档内容为空：{}", fileName);
                        return FileVisitResult.CONTINUE;
                    }

                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                    documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));

                    pgVectorStore.accept(documentSplitterList);

                } catch (Exception e) {
                    log.error("处理文件失败：{} - {}", fileName, e.getMessage(), e);
                }

                return FileVisitResult.CONTINUE;
            }

        });
        // 添加知识库标签
        RList<Object> ragTag = redissonClient.getList("ragTag");
        if (!ragTag.contains(repoProjectName)) {
            ragTag.add(repoProjectName);
        }
        log.info("添加知识库成功：{}", repoProjectName);
        log.info("遍历解析路径，上传完成:{}", repoUrl);

        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    // 我自己写的提取项目名的函数根据仓库地址
    private String exportRepoProjectName(String repoUrl) {
        return repoUrl.substring(repoUrl.lastIndexOf("/") + 1);
    }

    // 教程写的方法
    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }

    // 判断文件是否是代码文件
    private boolean isCodeOrDocFile(String fileName) {
        // 支持的代码文件扩展名
        String[] codeExtensions = {
                ".java", ".py", ".js", ".ts", ".go", ".cpp", ".c", ".h", ".cs",
                ".xml", ".json", ".yaml", ".yml", ".properties", ".sql", ".sh",
                ".md", ".txt", ".rst", ".adoc"
        };

        for (String ext : codeExtensions) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }




}
