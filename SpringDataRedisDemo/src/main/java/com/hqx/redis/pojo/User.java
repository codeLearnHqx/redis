package com.hqx.redis.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Description
 * @Create by hqx
 * @Date 2023/7/22 18:09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String name;
    private Integer age;

}
