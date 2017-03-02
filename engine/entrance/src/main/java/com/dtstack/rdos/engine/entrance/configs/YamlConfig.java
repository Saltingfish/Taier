package com.dtstack.rdos.engine.entrance.configs;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLConnection;
import org.yaml.snakeyaml.Yaml;


/**
 * 
 * Reason: TODO ADD REASON(可选)
 * Date: 2017年02月23日 下午1:25:45
 * Company: www.dtstack.com
 * @author sishu.yss
 *
 */
public class YamlConfig implements Config{
    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";

    @Override
    public <T> T parse(String filename,Class<T> classType) throws Exception{
            Yaml yaml = new Yaml();
            if (filename.startsWith(YamlConfig.HTTP) || filename.startsWith(YamlConfig.HTTPS)) {
                URL httpUrl;
                URLConnection connection;
                httpUrl = new URL(filename);
                connection = httpUrl.openConnection();
                connection.connect();
                return yaml.loadAs(connection.getInputStream(),classType);
            } else {
                FileInputStream input = new FileInputStream(new File(filename));
                return  yaml.loadAs(input,classType);
            }
    }
}