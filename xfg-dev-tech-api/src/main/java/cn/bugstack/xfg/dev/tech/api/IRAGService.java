package cn.bugstack.xfg.dev.tech.api;

import cn.bugstack.xfg.dev.tech.api.Response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface IRAGService {

    // 查询rag标签
    Response<List<String>> queryRagTagList();

    // 上传知识库文件
    Response<String> uploadFile(String ragTag, List<MultipartFile>  files);

    // 分析git仓库
    Response<String> analyzeGitRepository(String repoUrl, String userName, String token,String branch) throws IOException, Exception;
}
