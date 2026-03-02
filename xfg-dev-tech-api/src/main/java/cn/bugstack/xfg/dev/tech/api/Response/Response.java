package cn.bugstack.xfg.dev.tech.api.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> {
    private String code;
    private String info;
    private T data;
}
