package com.dcf.data.cache;

import com.dcf.data.model.CompanyData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据文件缓存（JSON 落盘）。
 *
 * <p>作用：避免每次启动重复请求外部接口；原始快照保留在本地便于离线复查。
 * 缓存有效期 24 小时，超过后自动失效由调用方重新抓取。
 */
public class DataCache {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final long TTL_MILLIS = 24L * 3600 * 1000;

    private final Path dir;

    public DataCache(Path dir) {
        this.dir = dir;
    }

    private Path fileFor(String code) {
        return dir.resolve("company_" + code + ".json");
    }

    /**
     * 读取缓存；不存在、损坏或过期返回 null。
     */
    public CompanyData load(String code) {
        try {
            Path f = fileFor(code);
            if (!Files.exists(f)) {
                return null;
            }
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis();
            if (age > TTL_MILLIS) {
                return null;
            }
            return MAPPER.readValue(f.toFile(), CompanyData.class);
        } catch (Exception e) {
            return null; // 缓存损坏视为未命中，自动重建
        }
    }

    /** 写入缓存（目录不存在时自动创建）。 */
    public void save(CompanyData data) {
        try {
            Files.createDirectories(dir);
            MAPPER.writeValue(fileFor(data.code()).toFile(), data);
        } catch (IOException ignored) {
            // 缓存失败不影响主流程
        }
    }

    /** 删除缓存文件。 */
    public void invalidate(String code) {
        try {
            Files.deleteIfExists(fileFor(code));
        } catch (IOException ignored) {
        }
    }
}