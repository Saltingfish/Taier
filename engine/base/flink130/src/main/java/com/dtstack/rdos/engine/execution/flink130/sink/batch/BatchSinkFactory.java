package com.dtstack.rdos.engine.execution.flink130.sink.batch;

import com.dtstack.rdos.commom.exception.RdosException;
import com.dtstack.rdos.common.util.PublicUtil;
import com.dtstack.rdos.engine.execution.base.operator.batch.BatchCreateResultOperator;
import com.dtstack.rdos.engine.execution.flink130.util.PluginSourceUtil;
import com.dtstack.rdos.engine.execution.loader.DtClassLoader;
import org.apache.flink.table.sinks.TableSink;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

/**
 * 获得table sink
 * Date: 2017/7/25
 * Company: www.dtstack.com
 * @ahthor xuchao
 */

public class BatchSinkFactory {

    public static String SINK_GENER_FUNC_NAME = "genBatchSink";

    public static TableSink getTableSink(BatchCreateResultOperator resultOperator) throws IOException,
            ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException {

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if(!(classLoader instanceof DtClassLoader)){
            throw new RdosException("it's not a correct classLoader instance, it's type must be DtClassLoader!");
        }

        String resultType = resultOperator.getType();
        String pluginJarPath = PluginSourceUtil.getJarFilePath(resultType);
        String className = PluginSourceUtil.getClassName(resultType);

        File pluginFile = new File(pluginJarPath);
        URL pluginJarURL = pluginFile.toURI().toURL();

        DtClassLoader dtClassLoader = (DtClassLoader) classLoader;
        dtClassLoader.addURL(pluginJarURL);
        Class<?> sinkClass = dtClassLoader.loadClass(className);
        for(Method method : sinkClass.getMethods()){
            if(method.getName().equals(SINK_GENER_FUNC_NAME)){
                Object object = sinkClass.newInstance();
                Object result = method.invoke(object, PublicUtil.ObjectToMap(resultOperator));
                return (TableSink) result;
            }
        }

        throw new RdosException("not support sink type:" + resultType + "!!!");
    }
}