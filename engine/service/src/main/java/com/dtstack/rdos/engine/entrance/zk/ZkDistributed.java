package com.dtstack.rdos.engine.entrance.zk;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.dtstack.rdos.	engine.db.dao.RdosNodeMachineDAO;
import com.dtstack.rdos.engine.entrance.zk.task.DataMigrationListener;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dtstack.rdos.commom.exception.ExceptionUtil;
import com.dtstack.rdos.commom.exception.RdosException;
import com.dtstack.rdos.engine.entrance.enumeration.RdosNodeMachineType;
import com.dtstack.rdos.engine.entrance.zk.data.BrokerDataNode;
import com.dtstack.rdos.engine.entrance.zk.data.BrokerHeartNode;
import com.dtstack.rdos.engine.entrance.zk.data.BrokersNode;
import com.dtstack.rdos.engine.entrance.zk.task.AllTaskStatusListener;
import com.dtstack.rdos.engine.entrance.zk.task.HeartBeat;
import com.dtstack.rdos.engine.entrance.zk.task.HeartBeatListener;
import com.dtstack.rdos.engine.entrance.zk.task.MasterListener;
import com.dtstack.rdos.engine.entrance.zk.task.RdosTaskStatusTaskListener;
import com.dtstack.rdos.engine.execution.base.enumeration.RdosTaskStatus;
import com.dtstack.rdos.engine.send.HttpSendClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.locks.InterProcessMutex;
import com.netflix.curator.retry.ExponentialBackoffRetry;


/**
 * 
 * Reason: TODO ADD REASON(可选)
 * Date: 2017年03月03日 下午1:25:18
 * Company: www.dtstack.com
 * @author sishu.yss
 *
 */
public class ZkDistributed {
	
	private static final Logger logger = LoggerFactory.getLogger(ZkDistributed.class);

	private Map<String,Object> nodeConfig;
	
	private String zkAddress;
	
	private String localAddress;

	private String distributeRootNode;

	private String brokersNode;

	private String localNode;
	
	private String heartNode = "heart";
	
	private String metaDataNode = "data";
	
	private CuratorFramework zkClient;
	
	private static ObjectMapper objectMapper = new ObjectMapper();

	private static ZkDistributed zkDistributed;
	
	private Map<String,BrokerDataNode> memTaskStatus = Maps.newHashMap();
	
	private InterProcessMutex masterlock;
	
	private InterProcessMutex brokerDataLock;

	private InterProcessMutex brokerHeartLock;

	private ExecutorService executors  = Executors.newFixedThreadPool(6);
	
	private RdosNodeMachineDAO rdosNodeMachineDAO = new RdosNodeMachineDAO();

	
	private ZkDistributed(Map<String,Object> nodeConfig) throws Exception {
		// TODO Auto-generated constructor stub
		this.nodeConfig  = nodeConfig;
		checkDistributedConfig();
		initZk();
	}

	public static ZkDistributed createZkDistributed(Map<String,Object> nodeConfig) throws Exception{
		if(zkDistributed == null){
			synchronized(ZkDistributed.class){
				if(zkDistributed == null){
					zkDistributed = new ZkDistributed(nodeConfig);
				}
			}
		}
		return zkDistributed;
	}
	
	private void initZk() throws IOException {
		this.zkClient = CuratorFrameworkFactory.builder()
		.connectString(this.zkAddress).retryPolicy(new ExponentialBackoffRetry(1000, 3))
		.connectionTimeoutMs(1000)
		.sessionTimeoutMs(1000).build();
		this.zkClient.start();
		logger.warn("connector zk success...");
	}
	
	public ZkDistributed zkRegistration() throws Exception {
		createNodeIfExists(this.distributeRootNode,"");
		createNodeIfExists(this.brokersNode,BrokersNode.initBrokersNode());
		createNodeIfExists(this.localNode,"");
		initNeedLock();
		createLocalBrokerHeartNode();
		createLocalBrokerDataNode();
		initMemTaskStatus();
		registrationDB();
		setMaster();
		initScheduledExecutorService();
		return this;
	}
	
	private void initScheduledExecutorService() {
		MasterListener masterListener = new MasterListener();
		executors.execute(new HeartBeat());
		executors.execute(masterListener);
		executors.execute(new HeartBeatListener(masterListener));
		executors.execute(new RdosTaskStatusTaskListener());
		executors.execute(new AllTaskStatusListener());
        executors.execute(new DataMigrationListener(masterListener));
	}
	
	private void registrationDB(){
		rdosNodeMachineDAO.insert(this.localAddress, RdosNodeMachineType.SLAVE.getType());
	}
	
	private void createLocalBrokerHeartNode() throws Exception{
		String node = String.format("%s/%s", this.localNode,heartNode);
		if (zkClient.checkExists().forPath(node) == null) {
			zkClient.create().forPath(node,
					objectMapper.writeValueAsBytes(BrokerHeartNode.initBrokerHeartNode()));
		}else{
			updateSynchronizedLocalBrokerHeartNode(this.localAddress,BrokerHeartNode.initBrokerHeartNode(),true);
		}
	}
	private void createLocalBrokerDataNode() throws Exception{
		String nodePath = String.format("%s/%s", this.localNode,metaDataNode);
		if (zkClient.checkExists().forPath(nodePath) == null) {
			zkClient.create().forPath(nodePath,
					objectMapper.writeValueAsBytes(BrokerDataNode.initBrokerDataNode()));
		}
	}
	
	public void updateSynchronizedLocalBrokerHeartNode(String localAddress,BrokerHeartNode source,boolean isCover){
		String nodePath = String.format("%s/%s/%s", brokersNode,localAddress,heartNode);
		try {
			this.brokerHeartLock.acquire(30, TimeUnit.SECONDS);
			BrokerHeartNode target = objectMapper.readValue(zkClient.getData().forPath(nodePath), BrokerHeartNode.class);
			BrokerHeartNode.copy(source, target,isCover);
			zkClient.setData().forPath(nodePath,
					objectMapper.writeValueAsBytes(target));
		} catch (Exception e) {
			logger.error("{}:updateSynchronizedBrokerHeartNode error:{}", nodePath,
					ExceptionUtil.getErrorMessage(e));
		} finally{
			try {
				if (this.brokerHeartLock.isAcquiredInThisProcess()) this.brokerHeartLock.release();
			} catch (Exception e) {
				logger.error("{}:updateSynchronizedBrokerHeartNode error:{}", nodePath,
						ExceptionUtil.getErrorMessage(e));
			}
		}
	}
		
	public void updateSynchronizedBrokerData(String localAddress,BrokerDataNode source,boolean isCover){
		String nodePath = String.format("%s/%s/%s",this.brokersNode,localAddress,metaDataNode);
		try {
			this.brokerDataLock.acquire(30, TimeUnit.SECONDS);
			BrokerDataNode target = objectMapper.readValue(zkClient.getData().forPath(nodePath), BrokerDataNode.class);
			BrokerDataNode.copy(source, target,isCover);
			zkClient.setData().forPath(nodePath,
					objectMapper.writeValueAsBytes(target));
		} catch (Exception e) {
			logger.error("{}:updateSynchronizedBrokerDatalock error:{}", nodePath,
					ExceptionUtil.getErrorMessage(e));
		} finally{
			try {
				if (this.brokerDataLock.isAcquiredInThisProcess()) this.brokerDataLock.release();
			} catch (Exception e) {
				logger.error("{}:updateSynchronizedBrokerDatalock error:{}", nodePath,
						ExceptionUtil.getErrorMessage(e));
			}
		}
	}
	
	
	public void updateSynchronizedLocalBrokerDataAndCleanNoNeedTask(String taskId,Integer status){
		String nodePath = String.format("%s/%s", this.localNode,metaDataNode);
		try {
			this.brokerDataLock.acquire(30, TimeUnit.SECONDS);
			BrokerDataNode target = objectMapper.readValue(zkClient.getData().forPath(nodePath), BrokerDataNode.class);
			Map<String,Byte> datas = target.getMetas();
			datas.put(taskId, status.byteValue());
			Set<String> keys = datas.keySet();
			for(String key:keys){
				if(RdosTaskStatus.needClean(datas.get(key))){
					datas.remove(key);
				}
			}
			zkClient.setData().forPath(nodePath,objectMapper.writeValueAsBytes(target));
		} catch (Exception e) {
			logger.error("{}:updateSynchronizedLocalBrokerDataAndCleanNoNeedTask error:{}", nodePath,
					ExceptionUtil.getErrorMessage(e));
		} finally{
			try {
				if (this.brokerDataLock.isAcquiredInThisProcess()) this.brokerDataLock.release();
			} catch (Exception e) {
				logger.error("{}:updateSynchronizedLocalBrokerDataAndCleanNoNeedTask error:{}", nodePath,
						ExceptionUtil.getErrorMessage(e));
			}
		}
	}
	
	
	private void initNeedLock(){
		this.masterlock = createDistributeLock(String.format(
				"%s/%s", this.distributeRootNode, "masterlock"));
		
		this.brokerDataLock = createDistributeLock(String.format(
				"%s/%s", this.distributeRootNode, "brokerdatalock"));
		
		this.brokerHeartLock = createDistributeLock(String.format(
				"%s/%s", this.distributeRootNode, "brokerheartlock"));
	}
	
	private InterProcessMutex createDistributeLock(String nodePath){
		return new InterProcessMutex(zkClient,nodePath);
	}
	
	public boolean setMaster() {
		boolean flag = false;
		try {
			String master = isHaveMaster();
			if (this.localAddress.equals(master))return true;
			boolean isMaster = this.masterlock.acquire(10, TimeUnit.SECONDS);
			if(isMaster){
				BrokersNode brokersNode = BrokersNode.initBrokersNode();
				brokersNode.setMaster(this.localAddress);
				this.zkClient.setData().forPath(this.brokersNode,
						objectMapper.writeValueAsBytes(brokersNode));
				rdosNodeMachineDAO.updateMachineType(this.localAddress,RdosNodeMachineType.MASTER.getType());
				if(StringUtils.isNotBlank(master)){
					rdosNodeMachineDAO.updateMachineType(master, RdosNodeMachineType.SLAVE.getType());
				}
				flag = true;
			}
		} catch (Exception e) {
			logger.error(ExceptionUtil.getErrorMessage(e));
		}
		return flag;
	}
	
	
	public String isHaveMaster() throws Exception {
		byte[] data = this.zkClient.getData().forPath(this.brokersNode);
		if (data == null
				|| StringUtils.isBlank(objectMapper.readValue(data,
						BrokersNode.class).getMaster())) {
			return null;
		}
		return objectMapper.readValue(data, BrokersNode.class).getMaster();
	}
	
	public void initMemTaskStatus(){
		synchronized(memTaskStatus){
			List<String> brokers = getBrokersChildren();
			for(String broker:brokers){
				BrokerHeartNode brokerHeartNode = getBrokerHeartNode(broker);
				if(brokerHeartNode.getAlive()){
					memTaskStatus.put(broker, getBrokerDataNode(broker));
				}else{
					memTaskStatus.remove(broker);
				}
			}
		}
	}
	
	public void updateLocalMemTaskStatus(BrokerDataNode brokerDataNode){
		synchronized(memTaskStatus){
			memTaskStatus.get(this.getLocalAddress()).getMetas().putAll(brokerDataNode.getMetas());
		}
	}


	public void createNodeIfExists(String node,Object obj) throws Exception{
			if (zkClient.checkExists().forPath(node) == null) {
				zkClient.create().forPath(node,
						objectMapper.writeValueAsBytes(obj));
			}
	}
	
	private void checkDistributedConfig() throws Exception {
		this.zkAddress = (String)nodeConfig.get("nodeZkAddress");
		if (StringUtils.isBlank(this.zkAddress)
				|| this.zkAddress.split("/").length < 2) {
			throw new RdosException("zkAddress is error");
		}
		String[] zks = this.zkAddress.split("/");
		this.zkAddress = zks[0].trim();
		this.distributeRootNode = String.format("/%s", zks[1].trim());
		this.localAddress = (String)nodeConfig.get("localAddress");
		if (StringUtils.isBlank(this.localAddress)||this.localAddress.split(":").length < 2) {
			throw new RdosException("localAddress is error");
		}
		this.brokersNode = String.format("%s/brokers", this.distributeRootNode);
		this.localNode = String.format("%s/%s", this.brokersNode,this.localAddress);
	}
	
	public BrokerDataNode getBrokerDataNode(String node) {
		try {
			String nodePath = String.format("%s/%s/%s", this.brokersNode, node,this.metaDataNode);
			BrokerDataNode nodeSign = objectMapper.readValue(zkClient.getData()
					.forPath(nodePath), BrokerDataNode.class);
			return nodeSign;
		} catch (Exception e) {
			logger.error("{}:getBrokerNodeData error:{}", node,
					ExceptionUtil.getErrorMessage(e));
		}
		return null;
	}
	
	public BrokerHeartNode getBrokerHeartNode(String node) {
		try {
			String nodePath = String.format("%s/%s/%s", this.brokersNode, node,this.heartNode);
			BrokerHeartNode nodeSign = objectMapper.readValue(zkClient.getData()
					.forPath(nodePath), BrokerHeartNode.class);
			return nodeSign;
		} catch (Exception e) {
			logger.error("{}:getBrokerHeartNode error:{}", node,
					ExceptionUtil.getErrorMessage(e));
		}
		return null;
	}
	
	
	public String getExcutionNode(){
		int def = Integer.MAX_VALUE;
		String node = this.localAddress;
		if(memTaskStatus.size() > 0){
			Set<Map.Entry<String,BrokerDataNode>> entrys  = memTaskStatus.entrySet();
			for(Map.Entry<String,BrokerDataNode> entry:entrys){
				int size = entry.getValue().getMetas().size();
				if(size < def){
					def = size;
					node = entry.getKey();
				}
			}
		}
		return node;
	}

	public boolean checkIsAlreadyInThisNode(String taskId){

		for(Map.Entry<String, BrokerDataNode> entry : memTaskStatus.entrySet()){
			for(String tmpId : entry.getValue().getMetas().keySet()){
				if(tmpId.equals(taskId)){
					return true;
				}
			}
		}

		return false;
	}
	
	public List<String> getBrokersChildren() {
		try {
			return zkClient.getChildren().forPath(this.brokersNode);
		} catch (Exception e) {
			logger.error("getBrokersChildren error:{}",
					ExceptionUtil.getErrorMessage(e));
		}
		return Lists.newArrayList();
	}

	public List<String> getAliveBrokersChildren() {
		try {
			List<String> brokers = zkClient.getChildren().forPath(this.brokersNode);
			for(String broker:brokers) {
				BrokerHeartNode brokerHeartNode = getBrokerHeartNode(broker);
				if (!brokerHeartNode.getAlive()) {
					brokers.remove(broker);
				}
			}
		} catch (Exception e) {
			logger.error("getBrokersChildren error:{}",
					ExceptionUtil.getErrorMessage(e));
		}
		return Lists.newArrayList();
	}


	public static ZkDistributed getZkDistributed(){
		return zkDistributed;
	}
	
	public String getLocalAddress() {
		return localAddress;
	}

	public Map<String,BrokerDataNode> getMemTaskStatus(){
		return memTaskStatus;
	}
	
	public void release(){
		// TODO Auto-generated method stub
        try{
            disableBrokerHeartNode(this.localAddress);
			if(getAliveBrokersChildren().size()>0){
				HttpSendClient.migration(this.localAddress);
			}
            executors.shutdown();
        }catch (Throwable e){
            logger.error("",e);
        }
	}

	public void disableBrokerHeartNode(String localAddress){
		BrokerHeartNode disableBrokerHeartNode = BrokerHeartNode.initNullBrokerHeartNode();
		zkDistributed.updateSynchronizedLocalBrokerHeartNode(localAddress,disableBrokerHeartNode, false);
	    this.rdosNodeMachineDAO.disableMachineNode(localAddress, RdosNodeMachineType.SLAVE.getType());
	}
	
	public void dataMigration(String nodeAddress) {
		// TODO Auto-generated method stub
		try {
			this.brokerDataLock.acquire(30, TimeUnit.SECONDS);
			Map<String,Byte> datas = cleanNoNeed(nodeAddress);
            BrokerHeartNode bNode = this.getBrokerHeartNode(nodeAddress);
			if(!bNode.getAlive()&&datas.size() >0){
				int total = datas.size();
				Map<String,Map<String,Byte>> others = Maps.newConcurrentMap();
				List<String> brokers = getBrokersChildren();
				for(String broker:brokers){
					BrokerHeartNode brokerHeartNode = getBrokerHeartNode(broker);
					if(brokerHeartNode.getAlive()){
						Map<String,Byte> bbs = cleanNoNeed(broker);
						others.put(broker, bbs);
						total = bbs.size() + total;
					}
				}
				if(others.size()>0){
					int a = total/others.size();
					List<Map.Entry<String,Map<String,Byte>>> otherList = Lists.newArrayList();
					A:for(Map.Entry<String,Map<String,Byte>> other:others.entrySet()){
						otherList.add(other);
						int index = 0;
						int c = other.getValue().size();
						if(c < a){
							B:for(Map.Entry<String,Byte> data:datas.entrySet()){
								index = index+1;
								if(index <= a-c){
									other.getValue().put(data.getKey(), data.getValue());
									datas.remove(data.getKey());
									continue B;
								}
								continue A;
							}
						}

					}
					if(datas.size() > 0){
						Collections.sort(otherList,
								new Comparator<Map.Entry<String,Map<String,Byte>>>() {
									@Override
									public int compare(Map.Entry<String,Map<String,Byte>> o1,
													   Map.Entry<String,Map<String,Byte>> o2) {
										return o1.getValue().size()
												- o2.getValue().size();
									}
								});
						int index = 0;
						for(Map.Entry<String, Byte> data:datas.entrySet()){
							otherList.get(index).getValue().put(data.getKey(), data.getValue());
							datas.remove(data.getKey());
							index = index +1;
						}
					}
					this.updateSynchronizedBrokerData(nodeAddress, BrokerDataNode.initBrokerDataNode(), true);
					for(Map.Entry<String,Map<String,Byte>> entry:otherList){
						BrokerDataNode brokerDataNode = BrokerDataNode.initBrokerDataNode();
						brokerDataNode.getMetas().putAll(entry.getValue());
						this.updateSynchronizedBrokerData(entry.getKey(), brokerDataNode, true);
					}
				}
			}
		} catch (Exception e) {
          logger.error("dataMigration fail:{}",ExceptionUtil.getErrorMessage(e));
		}finally{
			try {
				if(this.brokerDataLock.isAcquiredInThisProcess())this.brokerDataLock.release();
			} catch (Exception e) {
		          logger.error("dataMigration brokerDataLock release fail:{}",ExceptionUtil.getErrorMessage(e));
			}
		}
	}

	
	private Map<String,Byte> cleanNoNeed(String nodeAddress){
		BrokerDataNode brokerDataNode = this.getBrokerDataNode(nodeAddress);
		Map<String,Byte> datas = Maps.newConcurrentMap();
		if(brokerDataNode.getMetas()!=null) {
			datas.putAll(brokerDataNode.getMetas());
		}
		for(Map.Entry<String, Byte> data:datas.entrySet()){
			if(RdosTaskStatus.needClean(data.getValue())){
				datas.remove(data.getKey());
			}
		}
		return datas;
	}
}