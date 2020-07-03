import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import com.alibaba.fastjson.JSON;
import com.dtstack.engine.common.JobClient;
import com.dtstack.engine.common.akka.message.MessageJudgeSlots;
import com.dtstack.engine.common.akka.message.MessageSubmitJob;
import com.dtstack.engine.common.pojo.ParamAction;
import com.dtstack.engine.common.util.PublicUtil;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JobServiceTest {
    public static void main(String[] args) throws Exception {
        String str = "{\"isFailRetry\":true,\"sqlText\":\"use dev2;\\nSELECT count(1) FROM baixin;\\n\",\"computeType\":1,\"pluginInfo\":{\"sparkSqlProxyPath\":\"/dtInsight/sparkjars/spark-sql-proxy-1.0.0.jar\",\"spark.logConf\":\"true\",\"spark.yarn.appMasterEnv.PYSPARK_PYTHON\":\"/opt/dtstack/spark/bin/pyspark\",\"cluster\":\"default\",\"openKerberos\":false,\"spark.yarn.appMasterEnv.PYSPARK_DRIVER_PYTHON\":\"opt/dtstack/spark/bin/pyspark\",\"hiveConf\":{\"jdbcIdel\":\"1\",\"queryTimeout\":\"1000\",\"openKerberos\":false,\"checkTimeout\":\"10000\",\"password\":\"\",\"maxRows\":\"1000\",\"minPoolSize\":\"5\",\"useConnectionPool\":\"false\",\"jdbcUrl\":\"jdbc:hive2://kudu3:10000/%s\",\"driverClassName\":\"org.apache.hive.jdbc.HiveDriver\",\"maxPoolSize\":\"20\",\"initialPoolSize\":\"5\",\"username\":\"\"},\"typeName\":\"spark-yarn-hadoop2\",\"hadoopConf\":{\"fs.defaultFS\":\"hdfs://ns1\",\"dfs.namenode.shared.edits.dir\":\"qjournal://kudu1:8485;kudu2:8485;kudu3:8485/namenode-ha-data\",\"openKerberos\":false,\"hadoop.proxyuser.admin.groups\":\"*\",\"dfs.replication\":\"2\",\"dfs.ha.fencing.methods\":\"sshfence\",\"dfs.client.failover.proxy.provider.ns1\":\"org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider\",\"dfs.ha.fencing.ssh.private-key-files\":\"~/.ssh/id_rsa\",\"dfs.nameservices\":\"ns1\",\"dfs.safemode.threshold.pct\":\"0.5\",\"dfs.ha.namenodes.ns1\":\"nn1,nn2\",\"hadoop.tmp.dir\":\"/data/hadoop_admin\",\"dfs.journalnode.edits.dir\":\"/opt/dtstack/hadoop/journal\",\"dfs.journalnode.rpc-address\":\"0.0.0.0:8485\",\"dfs.namenode.http-address.ns1.nn2\":\"kudu2:50070\",\"dfs.namenode.http-address.ns1.nn1\":\"kudu1:50070\",\"dfs.journalnode.http-address\":\"0.0.0.0:8480\",\"hadoop.proxyuser.admin.hosts\":\"*\",\"dfs.namenode.rpc-address.ns1.nn2\":\"kudu2:9000\",\"dfs.namenode.rpc-address.ns1.nn1\":\"kudu1:9000\",\"dfs.ha.automatic-failover.enabled\":\"true\"},\"confHdfsPath\":\"/home/admin/app/tmp/console/hadoop_config/default\",\"yarnConf\":{\"yarn.resourcemanager.zk-address\":\"kudu1:2181,kudu2:2181,kudu3:2181\",\"yarn.resourcemanager.admin.address.rm1\":\"kudu1:8033\",\"yarn.log.server.url\":\"http://kudu2:19888/jobhistory/logs/\",\"yarn.resourcemanager.webapp.address.rm2\":\"kudu2:8088\",\"yarn.resourcemanager.admin.address.rm2\":\"kudu2:8033\",\"yarn.resourcemanager.webapp.address.rm1\":\"kudu1:8088\",\"yarn.resourcemanager.ha.rm-ids\":\"rm1,rm2\",\"yarn.resourcemanager.ha.automatic-failover.zk-base-path\":\"/yarn-leader-election\",\"yarn.client.failover-proxy-provider\":\"org.apache.hadoop.yarn.client.ConfiguredRMFailoverProxyProvider\",\"yarn.resourcemanager.scheduler.address.rm1\":\"kudu1:8030\",\"yarn.resourcemanager.scheduler.address.rm2\":\"kudu2:8030\",\"yarn.nodemanager.delete.debug-delay-sec\":\"600\",\"yarn.resourcemanager.address.rm1\":\"kudu1:8032\",\"yarn.log-aggregation.retain-seconds\":\"2592000\",\"yarn.nodemanager.resource.memory-mb\":\"8000\",\"yarn.resourcemanager.ha.enabled\":\"true\",\"yarn.resourcemanager.address.rm2\":\"kudu2:8032\",\"yarn.resourcemanager.cluster-id\":\"yarn-rm-cluster\",\"yarn.scheduler.minimum-allocation-mb\":\"512\",\"yarn.nodemanager.aux-services\":\"mapreduce_shuffle\",\"yarn.nodemanager.resource.cpu-vcores\":\"6\",\"yarn.resourcemanager.resource-tracker.address.rm1\":\"kudu1:8031\",\"yarn.resourcemanager.resource-tracker.address.rm2\":\"kudu2:8031\",\"openKerberos\":false,\"yarn.nodemanager.remote-app-log-dir\":\"/tmp/logs\",\"yarn.resourcemanager.ha.automatic-failover.enabled\":\"true\",\"yarn.nodemanager.vmem-check-enabled\":\"false\",\"yarn.resourcemanager.hostname.rm2\":\"kudu2\",\"yarn.nodemanager.webapp.address\":\"kudu3:8042\",\"yarn.resourcemanager.hostname.rm1\":\"kudu1\",\"yarn.nodemanager.aux-services.mapreduce_shuffle.class\":\"org.apache.hadoop.mapred.ShuffleHandler\",\"yarn.log-aggregation-enable\":\"true\",\"yarn.resourcemanager.recovery.enabled\":\"true\",\"yarn.resourcemanager.store.class\":\"org.apache.hadoop.yarn.server.resourcemanager.recovery.ZKRMStateStore\",\"yarn.nodemanager.vmem-pmem-ratio\":\"4\",\"yarn.resourcemanager.zk-state-store.address\":\"kudu1:2181,kudu2:2181,kudu3:2181\"},\"sftpConf\":{\"path\":\"/home/admin/app/tmp\",\"password\":\"abc123\",\"port\":\"22\",\"auth\":\"1\",\"host\":\"kudu1\",\"username\":\"root\"},\"sparkPythonExtLibPath\":\"/dtInsight/sparkjars/pythons/pyspark.zip,/dtInsight/sparkjars/pythons/py4j-0.10.7-src.zip\",\"spark.eventLog.compress\":\"true\",\"sparkYarnArchive\":\"/dtInsight/sparkjars/jars\",\"spark.eventLog.enabled\":\"true\",\"spark.eventLog.dir\":\"hdfs://ns1/tmp/history\",\"md5zip\":\"6a3551b91451b79caf658e35a8995e3a\",\"tenantId\":1,\"queue\":\"c\"},\"engineType\":\"spark\",\"taskParams\":\"##Driver程序使用的CPU核数,默认为1\\n##driver.cores=1\\n\\n##Driver程序使用内存大小,默认512m\\n##driver.memory=512m\\n\\n##对Spark每个action结果集大小的限制，最少是1M，若设为0则不限制大小。\\n##若Job结果超过限制则会异常退出，若结果集限制过大也可能造成OOM问题，默认1g\\n##driver.maxResultSize=1g\\n\\n##SparkContext 启动时是否记录有效 SparkConf信息,默认false\\n##logConf=false\\n\\n##启动的executor的数量，默认为1\\nexecutor.instances=1\\n\\n#每个executor使用的CPU核数，默认为1\\nexecutor.cores=1\\n\\n##每个executor内存大小,默认512m\\n##executor.memory=512m\\n\\n##任务优先级, 值越小，优先级越高，范围:1-1000\\njob.priority=10\\n\\n##spark 日志级别可选ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN\\n##logLevel = INFO\\n\\n##spark中所有网络交互的最大超时时间\\n#spark.network.timeout=120s\\n\\n##executor的OffHeap内存，和spark.executor.memory配置使用\\n#spark.yarn.executor.memoryOverhead\",\"maxRetryNum\":3,\"taskType\":0,\"groupName\":\"default_c\",\"clusterName\":\"default\",\"name\":\"cronJob_dddde3333_20200223143000\",\"tenantId\":1,\"taskId\":\"7947af41\"}";
        Map params = (Map)JSON.parse(str);
        ParamAction paramAction = PublicUtil.mapToObject(params, ParamAction.class);
        JobClient jobClient = new JobClient(paramAction);
        MessageSubmitJob message = new MessageSubmitJob(jobClient);
        ActorSystem system = ActorSystem.create("AkkaRemoteTest", ConfigFactory.load());
        ActorSelection actorRef = system.actorSelection("akka.tcp://akkaRemoteWork@127.0.0.1:2553/user/Worker");
        Future<Object> future = Patterns.ask(actorRef, message, 60000);
        Object result = Await.result(future, Duration.create(60, TimeUnit.SECONDS));
        System.out.println(result);
    }
}