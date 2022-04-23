package simblock.simulator;


import static simblock.settings.NetworkConfiguration.*;
import static simblock.settings.SimulationConfiguration.ALGO;
import static simblock.settings.SimulationConfiguration.AVERAGE_MINING_POWER;
import static simblock.settings.SimulationConfiguration.END_BLOCK_HEIGHT;
import static simblock.settings.SimulationConfiguration.INTERVAL;
import static simblock.settings.SimulationConfiguration.NUM_OF_NODES;
import static simblock.settings.SimulationConfiguration.STDEV_OF_MINING_POWER;
import static simblock.settings.SimulationConfiguration.TABLE;
import static simblock.settings.SimulationConfiguration.CBR_USAGE_RATE;
import static simblock.settings.SimulationConfiguration.CHURN_NODE_RATE;
import static simblock.simulator.Network.getDegreeDistribution;
import static simblock.simulator.Network.getRegionDistribution;
import static simblock.simulator.Network.printRegion;
import static simblock.simulator.Simulator.addNode;
import static simblock.simulator.Simulator.getSimulatedNodes;
import static simblock.simulator.Simulator.printAllPropagation;
import static simblock.simulator.Simulator.setTargetInterval;
import static simblock.simulator.Timer.getCurrentTime;
import static simblock.simulator.Timer.getTask;
import static simblock.simulator.Timer.runTask;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import simblock.block.Block;
import simblock.node.Node;
import simblock.task.AbstractMintingTask;


public class Main {
    /**
     * The constant to be used as the simulation seed.
     */
    public static Random random = new Random(10);

    /**
     * The initial simulation time.
     */
    public static long simulationTime = 0;

    public static URI CONF_FILE_URI;//配置文件路径

    public static URI OUT_FILE_URI;//输出文件路径

    static {
        try {
            CONF_FILE_URI = ClassLoader.getSystemResource("simulator.conf").toURI();

            OUT_FILE_URI = CONF_FILE_URI.resolve(new URI("../output/"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /**
     * The output writer.
     */
    //TODO use logger
    public static PrintWriter OUT_JSON_FILE;

    /**
     * The constant STATIC_JSON_FILE.
     */
    //TODO use logger
    public static PrintWriter STATIC_JSON_FILE;

    static {
        try {
            OUT_JSON_FILE = new PrintWriter(
                    new BufferedWriter(new FileWriter(new File(OUT_FILE_URI.resolve("./output.json")))));
            STATIC_JSON_FILE = new PrintWriter(
                    new BufferedWriter(new FileWriter(new File(OUT_FILE_URI.resolve("./static.json")))));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //主函数入口
    public static void main(String[] args) {
        final long start = System.currentTimeMillis();
        // 设置出块时间
        setTargetInterval(INTERVAL);

        //开始输出json
        OUT_JSON_FILE.print("[");
        OUT_JSON_FILE.flush();

        // 打印区域
        printRegion();

//        String filePath = "simulator/src/dist/conf/data/init_data_BETWEENNESS.json";
//        String filePath = "simulator/src/dist/conf/data/init_data_BETWEENNESS_1.5%.json";
//        String filePath = "simulator/src/dist/conf/data/init_data_BETWEENNESS_2%.json";

//        String filePath = "simulator/src/dist/conf/data/init_data_DEGREE.json";
//        String filePath = "simulator/src/dist/conf/data/init_data_DEGREE_1.5%.json";
//        String filePath = "simulator/src/dist/conf/data/init_data_DEGREE_2%.json";

//        String filePath = "simulator/src/dist/conf/data/init_data_MY_1%.json";
//        String filePath = "simulator/src/dist/conf/data/init_data_MY_1.5%.json";
//        String filePath = "simulator/src/dist/conf/data/init_data_MY_2%.json";
        String filePath = "simulator/src/dist/conf/data/init_data_row.json";
        constructNetworkWithGivenFile(filePath);
//    constructNetworkWithAllNodes(NUM_OF_NODES);

        // 初始区块高度, we stop at END_BLOCK_HEIGHT;在指定区块高度结束;区块高度就是目前生成了多少个区块而已
        int currentBlockHeight = 1;

        // 遍历task并处理
        while (getTask() != null) {
            if (getTask() instanceof AbstractMintingTask) {
                AbstractMintingTask task = (AbstractMintingTask) getTask();
                if (task.getParent().getHeight() == currentBlockHeight) {
                    currentBlockHeight++;
                }
                if (currentBlockHeight > END_BLOCK_HEIGHT) {
                    break;
                }
                // Log every 100 blocks and at the second block
                // TODO use constants here
                if (currentBlockHeight % 100 == 0 || currentBlockHeight == 2) {
                    writeGraph(currentBlockHeight);
                }
            }
            // 执行task
            runTask();//挖
        }

        // 打印所有区块的传播信息
        printAllPropagation();

        //TODO logger
        System.out.println();

        Set<Block> blocks = new HashSet<>();

        Block block = getSimulatedNodes().get(0).getBlock();

        //通过添加上述块的父块来更新已知块的列表
        while (block.getParent() != null) {
            blocks.add(block);
            block = block.getParent();
        }

        Set<Block> orphans = new HashSet<>();
        int averageOrphansSize = 0;
        // Gather all known orphans
        for (Node node : getSimulatedNodes()) {
            orphans.addAll(node.getOrphans());
            averageOrphansSize += node.getOrphans().size();
        }
        averageOrphansSize = averageOrphansSize / getSimulatedNodes().size();

        // Record orphans to the list of all known blocks
        blocks.addAll(orphans);

        ArrayList<Block> blockList = new ArrayList<>(blocks);

        //首先按时间排序块，然后按哈希值排序
        blockList.sort((a, b) -> {
            int order = Long.signum(a.getTime() - b.getTime());
            if (order != 0) {
                return order;
            }
            order = System.identityHashCode(a) - System.identityHashCode(b);
            return order;
        });

        //Log all orphans
        // TODO move to method and use logger
        for (Block orphan : orphans) {
            System.out.println("孤块----" + orphan + ":" + orphan.getHeight());
        }
        System.out.println(averageOrphansSize);

    /*
    Log in format:
     ＜fork_information, block height, block ID＞
    fork_information: One of "OnChain" and "Orphan". "OnChain" 表示区块在主链上.
    "Orphan"表示区块是孤立的.
     */
        // TODO move to method and use logger
        try {
            FileWriter fw = new FileWriter(new File(OUT_FILE_URI.resolve("./blockList.txt")), false);
            PrintWriter pw = new PrintWriter(new BufferedWriter(fw));

            for (Block b : blockList) {
                if (!orphans.contains(b)) {
                    pw.println("OnChain : " + b.getHeight() + " : " + b);
                } else {
                    pw.println("Orphan : " + b.getHeight() + " : " + b);
                }
            }
            pw.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        OUT_JSON_FILE.print("{");
        OUT_JSON_FILE.print("\"kind\":\"simulation-end\",");
        OUT_JSON_FILE.print("\"content\":{");
        OUT_JSON_FILE.print("\"timestamp\":" + getCurrentTime());
        OUT_JSON_FILE.print("}");
        OUT_JSON_FILE.print("}");
        //end json format
        OUT_JSON_FILE.print("]");
        OUT_JSON_FILE.close();


        long end = System.currentTimeMillis();
        simulationTime += end - start;
        // Log simulation time in milliseconds
        System.out.println("simulationTime : " + simulationTime + " ms");

    }


    //TODO　以下の初期生成はシナリオを読み込むようにする予定
    //ノードを参加させるタスクを作る(ノードの参加と，リンクの貼り始めるタスクは分ける)
    //シナリオファイルで上の参加タスクをTimer入れていく．

    // TRANSLATED FROM ABOVE STATEMENT
    // The following initial generation will load the scenario
    // Create a task to join the node (separate the task of joining the node and the task of
    // starting to paste the link)
    // Add the above participating tasks with a timer in the scenario file.

    /**
     * Populate the list using the distribution.
     *
     * @param distribution the distribution
     * @param facum        分布是否为累积分布
     * @return array list
     */
    //TODO explanation on facum etc.
    public static ArrayList<Integer> makeRandomListFollowDistribution(double[] distribution, boolean facum) {
        ArrayList<Integer> list = new ArrayList<>();
        int index = 0;

        if (facum) {
            // 传入的参数是(degreeList,true);累积分布
            for (; index < distribution.length; index++) {
                while (list.size() <= NUM_OF_NODES * distribution[index]) {
                    list.add(index);
                }
            }
            while (list.size() < NUM_OF_NODES) {
                list.add(index);
            }
        } else {//不是累积分布
            //      如果是传入的参数是(regionList,false);list里面存放的值是各个节点的所属的区域的id
            double acumulative = 0.0;
            for (; index < distribution.length; index++) {
                acumulative += distribution[index];
                while (list.size() <= NUM_OF_NODES * acumulative) {
                    list.add(index);
                }
            }
            while (list.size() < NUM_OF_NODES) {
                list.add(index);
            }
        }
//然后各个节点打乱(所属的区域的id打乱)
        Collections.shuffle(list, random);
        return list;
    }

    /**
     * Populate the list using the rate.
     *
     * @param rate the rate of true
     * @return array list
     */
    public static ArrayList<Boolean> makeRandomList(float rate) {
        ArrayList<Boolean> list = new ArrayList<Boolean>();
        for (int i = 0; i < NUM_OF_NODES; i++) {
            list.add(i < NUM_OF_NODES * rate);
        }
        Collections.shuffle(list, random);
        return list;
    }

    public static ArrayList<Boolean> makeRandomListTRUE() {
        ArrayList<Boolean> list = new ArrayList<Boolean>();
        for (int i = 0; i < NUM_OF_NODES; i++) {
            list.add(true);
        }
        return list;
    }

    /**
     * Generates a random mining power expressed as Hash Rate, and is the number of mining (hash
     * calculation) executed per millisecond.
     *
     * @return the number of hash  calculations executed per millisecond.
     */
    public static int genMiningPower() {
        double r = random.nextGaussian();

        return Math.max((int) (r * STDEV_OF_MINING_POWER + AVERAGE_MINING_POWER), 1);
    }

    /**
     * 重写genMiningPower方法，每个region的哈希率成比例，region内的哈希率成Gaussion分布
     * calculation) executed per millisecond.
     *
     * @return the number of hash  calculations executed per millisecond.
     */
    public static int genMiningPower(Integer regionID) {
        double r = random.nextGaussian();

        return Math.max((int) (r * STDEV_OF_MINING_POWER + AVERAGE_MINING_POWER) * HASHRATE_LIST[regionID], 1);
    }


    /**
     * 根据所提供的节点数量 构建网络;
     *
     * @param numNodes the num nodes
     */
    public static void constructNetworkWithAllNodes(int numNodes) {

        // 每个区域节点的随机分布比率，返回的double数组是指定好的，一共有6个区域，double[]有6个值
        double[] regionDistribution = getRegionDistribution();
//    regionList存放的是NUM_NODES个值,每个值的范围是0-5,也就是区域的id;被随机打乱
        List<Integer> regionList = makeRandomListFollowDistribution(regionDistribution, false);
//    test:
//    System.out.println("regionList:");
//    for(Integer i:regionList){
//      System.out.println(i+"   ");
//    }
//    System.out.println(" regionList size "+regionList.size());

        // 节点度的随机分布
        double[] degreeDistribution = getDegreeDistribution();
        List<Integer> degreeList = makeRandomListFollowDistribution(degreeDistribution, true);

//    System.out.println("dList:");
//    for(Integer i:degreeList){
//      System.out.println(i+"   ");
//    }
//    System.out.println(" dList size "+degreeList.size());

        // List of nodes using compact block relay.使用紧凑块中继的节点列表。
        List<Boolean> useCBRNodes = makeRandomList(CBR_USAGE_RATE);

        // List of churn nodes.流失节点列表
        List<Boolean> churnNodes = makeRandomList(CHURN_NODE_RATE);

        /* 确定numNodes个节点 所在的区域，度数、mining power(算力)，路由表，共识算法等等 */

        for (int id = 1; id <= numNodes; id++) {
            Node node = new Node(
                    id, degreeList.get(id - 1) + 1, regionList.get(id - 1), genMiningPower(), TABLE,
                    ALGO, useCBRNodes.get(id - 1), churnNodes.get(id - 1)
            );
            // 将模拟节点添加到模拟节点列表中
            addNode(node);

            OUT_JSON_FILE.print("{");
            OUT_JSON_FILE.print("\"kind\":\"add-node\",");
            OUT_JSON_FILE.print("\"content\":{");
            OUT_JSON_FILE.print("\"timestamp\":0,");
            OUT_JSON_FILE.print("\"node-id\":" + id + ",");
            OUT_JSON_FILE.print("\"region-id\":" + regionList.get(id - 1));
            OUT_JSON_FILE.print("}");
            OUT_JSON_FILE.print("},");
            OUT_JSON_FILE.flush();

        }

        // 模拟节点列表中的节点加入区块链网络,也就是在初始化节点的路由表
        for (Node node : getSimulatedNodes()) {
            node.joinNetwork();
        }

        //  指定一个随机节点(list中的节点是随机的)来生成genesis block
        getSimulatedNodes().get(0).genesisBlock();
    }

    /*
     *
     * */
    public static String readJsonFile(String fileName) {
        String jsonStr = "";
        try {
            File jsonFile = new File(fileName);
            FileReader fileReader = new FileReader(jsonFile);

            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), "utf-8");
            int ch = 0;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            fileReader.close();
            reader.close();
            jsonStr = sb.toString();
            return jsonStr;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 根据输入数据，确定每个节点的r值
     * 同一个region内的所有节点的r值随机 呈Gaussion分布
     * 返回一个HashMap；每个nodeID指定一个r值
     * String：nodeID
     * Double：r值
     */
    public static HashMap<String, Double> getValueOfRByGaussion(String filePath) {
        /**
         * ArrayList.get(i)：当前regionID=i 里面包含的所有Node :vector
         */
        ArrayList<Vector<String>> allNodesByRegionID = new ArrayList<>();
        String s = Main.readJsonFile(filePath);
        JSONObject jobj = JSON.parseObject(s);
        Integer node_total = (Integer) jobj.get("Num");
        /**
         * 将所有节点按region分类
         */
        for (int i = 1; i <= node_total; i++) {
            String name = "nodeNum_" + i;
            JSONObject nodeInfo = jobj.getJSONObject(name);
            String region = (String) nodeInfo.get("region");
            String nodeID = (String) nodeInfo.get("nodeid");
            for (int j = 0; j < REGION_LIST.size(); j++) {
                if (REGION_LIST.get(j).equals(region))
                    allNodesByRegionID.get(j).add(nodeID);
            }
        }
        /**
         * 每类region里面的每一个node的r值 随机Gaussian分布
         * 每个nodeID对应一个r值
         */
        HashMap<String, Double> nodeID_R = new HashMap<>();
        for (int i = 0; i < allNodesByRegionID.size(); i++) {
            Vector<String> region = allNodesByRegionID.get(i);
            for (int j = 0; j < region.size(); j++) {
                nodeID_R.put(region.get(j), random.nextGaussian());
            }
        }
        return nodeID_R;
    }


    /**
     * 根据给定的拓扑结构生成网络
     */
    public static void constructNetworkWithGivenFile(String filePath) {
//     String path = Main.class.getClassLoader().getResource("init_data.json").getPath();
        String s = Main.readJsonFile(filePath);
        JSONObject jobj = JSON.parseObject(s);

        Integer node_total = (Integer) jobj.get("Num");
        NUM_OF_NODES = node_total;

        // List of nodes using compact block relay.使用紧凑块中继的节点列表。
        List<Boolean> useCBRNodes = makeRandomListTRUE();

        // List of churn nodes.流失节点列表
        List<Boolean> churnNodes = makeRandomListTRUE();

        /* 确定numNodes个节点 所在的区域，度数、mining power(算力)，路由表，共识算法等等 */
        ArrayList<Integer> degreeList = new ArrayList<>();
        ArrayList<Integer> regionList = new ArrayList<>();
        ArrayList<ArrayList<Integer>> neighborList = new ArrayList<ArrayList<Integer>>();


        /**
         *  处理json中的每一个节点
         */
        for (int i = 1; i <= node_total; i++) {
            String name = "nodeNum_" + i;
            JSONObject nodeInfo = jobj.getJSONObject(name);
            if (nodeInfo == null) continue;
            //度数
//            if(nodeInfo.get("NumConnections")==null)
//                System.out.println(name);
            Integer node_degree = (Integer) nodeInfo.get("NumConnections");
            degreeList.add(node_degree);
            //区域
            String region = (String) nodeInfo.get("region");
            for (int j = 0; j < REGION_LIST.size(); j++) {
                if (REGION_LIST.get(j).equals(region))
                    regionList.add(j);
            }
            //邻居
            JSONArray neighborsJson = nodeInfo.getJSONArray("neighbors");
            ArrayList<Integer> neighbors = new ArrayList<>();

            for (int j = 0; j < neighborsJson.size(); j++)
                neighbors.add(neighborsJson.getIntValue(j));
            neighborList.add(neighbors);
        }

        for (int id = 1; id <= node_total; id++) {
            Node node = new Node(
                    id, degreeList.get(id - 1) + 1, regionList.get(id - 1), genMiningPower(regionList.get(id - 1)), TABLE,
                    ALGO, useCBRNodes.get(id - 1), churnNodes.get(id - 1)
            );
//            System.out.println("算力： "+node.getMiningPower());
            // 将模拟节点添加到模拟节点列表中
            addNode(node);
            OUT_JSON_FILE.print("{");
            OUT_JSON_FILE.print("\"kind\":\"add-node\",");
            OUT_JSON_FILE.print("\"content\":{");
            OUT_JSON_FILE.print("\"timestamp\":0,");
            OUT_JSON_FILE.print("\"node-id\":" + id + ",");
            OUT_JSON_FILE.print("\"region-id\":" + regionList.get(id - 1));
            OUT_JSON_FILE.print("}");
            OUT_JSON_FILE.print("},");
            OUT_JSON_FILE.flush();
        }
//     check here
        // 模拟节点列表中的节点加入区块链网络,也就是在初始化节点的路由表
        ArrayList<Node> SimulatedNodes = getSimulatedNodes();
        for (int i = 0; i < SimulatedNodes.size(); i++) {
//            System.out.println(neighborList.get(i));
            Node currentNode = SimulatedNodes.get(i);
            ArrayList<Integer> curNeighbors = neighborList.get(i);
            currentNode.joinNetwork(curNeighbors);
        }

        //  指定一个随机节点(list中的节点是随机的)来生成genesis block
        getSimulatedNodes().get(0).genesisBlock();
    }


    /**
     * Network information when block height is <em>blockHeight</em>, in format:
     *
     * <p><em>nodeID_1</em>, <em>nodeID_2</em>
     *
     * <p>meaning there is a connection from nodeID_1 to right nodeID_1.
     *
     * @param blockHeight the index of the graph and the current block height
     */
    //TODO use logger
    public static void writeGraph(int blockHeight) {
        try {
            FileWriter fw = new FileWriter(
                    new File(OUT_FILE_URI.resolve("./graph/" + blockHeight + ".txt")), false);
            PrintWriter pw = new PrintWriter(new BufferedWriter(fw));

            for (int index = 1; index <= getSimulatedNodes().size(); index++) {
                Node node = getSimulatedNodes().get(index - 1);
                for (int i = 0; i < node.getNeighbors().size(); i++) {
                    Node neighbor = node.getNeighbors().get(i);
                    pw.println(node.getNodeID() + " " + neighbor.getNodeID());
                }
            }
            pw.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
