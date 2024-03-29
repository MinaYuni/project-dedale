package eu.su.mas.dedaleEtu.mas.knowledge;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.*;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSourceGraphML;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.view.Viewer;

import dataStructures.serializableGraph.*;
import dataStructures.tuple.Couple;
import javafx.application.Platform;

/**
 * This simple topology representation only deals with the graph, not its content.</br>
 * The knowledge representation is not well written (at all), it is just given as a minimal example.</br>
 * The viewer methods are not independent of the data structure, and the dijkstra is recomputed every-time.
 *
 * @author hc
 */
public class FullMapRepresentation implements Serializable {

    private static final long serialVersionUID = -8333959882640838272L;

    /*********************************
     * Parameters for graph rendering
     ********************************/

    private final String defaultNodeStyle = "node {" + "fill-color: black;" + " size-mode:fit;text-alignment:under; text-size:14;text-color:white;text-background-mode:rounded-box;text-background-color:black;}";
    private final String nodeStyle_agent = "node.agent {" + "fill-color: green;" + "}";
    private final String nodeStyle_open = "node.open {" + "fill-color: red;" + "}";
    private final String nodeStyle = defaultNodeStyle + nodeStyle_agent + nodeStyle_open;
    private Graph g; //data structure non serializable
    private Viewer viewer; //ref to the display,  non serializable
    private Integer nbEdges;//used to generate the edges ids
    private Integer nbNodes;
    private SerializableSimpleGraph<String, HashMap<String, Object>> sg;//used as a temporary dataStructure during migration
    private HashMap<String, Couple<Integer, String>> goldDict = new HashMap<String, Couple<Integer, String>>(); // key: nodeId, value: quantité d'or
    private HashMap<String, Couple<Integer, String>> diamondDict = new HashMap<String, Couple<Integer, String>>(); // key: nodeId, value: quantité de diamant

    public FullMapRepresentation() {
        //System.setProperty("org.graphstream.ui.renderer","org.graphstream.ui.j2dviewer.J2DGraphRenderer");
        System.setProperty("org.graphstream.ui", "javafx");
        this.g = new SingleGraph("My world vision");
        this.g.setAttribute("ui.stylesheet", nodeStyle);

        Platform.runLater(this::openGui);
        //this.viewer = this.g.display();

        this.nbEdges = 0;
        this.nbNodes = 0;
    }

    /**
     * A node is open, closed, or agent
     *
     * @author hc
     */
    public enum MapAttribute {
        agent, open, closed
    }

    /*--------------------- GET et SET ---------------------------*/

    public int getNbEdges() {
        return this.nbEdges;
    }

    public int getNbNodes() {
        return this.nbNodes;
    }

    public HashMap<String, Couple<Integer, String>> getDiamondDict() {
        return diamondDict;
    }

    public HashMap<String, Couple<Integer, String>> getGoldDict() {
        return goldDict;
    }

    public SerializableSimpleGraph<String, HashMap<String, Object>> getSg() {
        return this.sg;
    }

    public void setSg(SerializableSimpleGraph<String, HashMap<String, Object>> sgreceived) {
        this.sg = sgreceived;
    }

    public void updateGoldDictExplo(HashMap<String, Couple<Integer, String>> dict){
        if (dict != null) {
            for (String node : dict.keySet()) {

                if (this.goldDict.containsKey(node)) {
                    // prend la quantité de gold la plus récente
                    int comparaison = (dict.get(node).getRight()).compareTo(this.goldDict.get(node).getRight());
                    if (comparaison >= 0) {
                        this.goldDict.put(node, dict.get(node));
                    }
                }else{
                    // on découvre du gold dans un noeud node
                    this.goldDict.put(node, dict.get(node));
                }
            }
        }
    };
    public void updateDiamondDictExplo(HashMap<String, Couple<Integer, String>> dict){
        if (dict != null) {
            for (String node : dict.keySet()) {
                if (this.diamondDict.containsKey(node)) {
                    // prend la quantité de gold la plus récente
                    int comparaison = (dict.get(node).getRight()).compareTo(this.diamondDict.get(node).getRight());
                    if (comparaison >= 0) {
                        this.diamondDict.put(node, dict.get(node));
                    }
                }else{
                    // on découvre du gold dans un noeud node
                    this.diamondDict.put(node, dict.get(node));
                }
            }
        }
    };

    /*--------------------- Méthodes add or remove node/edge ---------------------------*/

    /**
     * Add or replace a node and its attribute
     *
     * @param id                id du noeud
     * @param mapAttribute      attributs des noeuds de la map
     * @param lObservations     liste des observations du noeud id
     * @param time              temps où le noeud a été observé
     */
    public synchronized void addNode(String id, MapAttribute mapAttribute, List<Couple<Observation, Integer>> lObservations, long time) {
        Node n = this.g.getNode(id);

        if ( n == null) {
            this.nbNodes++;
            n = this.g.addNode(id);
        }
        n.clearAttributes();
        n.setAttribute("ui.class", mapAttribute.toString());
        n.setAttribute("ui.label", id);
        n.setAttribute("timestamp", String.valueOf(time));

        for (Couple<Observation, Integer> o : lObservations) {
            Observation observationType = o.getLeft(); // diamond ou gold
            Integer observationValue = o.getRight();   // nombre de ressources (diamond ou gold)
            Couple <Integer, String> value = new Couple<Integer, String>(observationValue, String.valueOf(time));

            switch (observationType) {
                case DIAMOND:
                    this.diamondDict.put(id, value);
                    break;
                case GOLD:
                    this.goldDict.put(id, value);
                    break;
                //case STENCH:
                default:
                    break;
            }
        }
    }

    /**
     * Add a node to the graph. Do nothing if the node already exists.
     * If new, it is labeled as open (non-visited)
     *
     * @param id                id du noeud
     * @param lObservations     liste des observations du noeud id
     * @param time              temps où le noeud a été observé
     *
     * @return true if added
     */
    public synchronized boolean addNewNode(String id, List<Couple<Observation, Integer>> lObservations, long time) {
        if (this.g.getNode(id) == null) {
            addNode(id, MapAttribute.open, lObservations, time);
            this.nbNodes++;
            return true;
        }
        return false;
    }

    /**
     * Add an undirect edge if not already existing.
     *
     * @param idNode1   id of node 1
     * @param idNode2   id of node 2
     */
    public synchronized void addEdge(String idNode1, String idNode2) {
        this.nbEdges++;
        try {
            this.g.addEdge(this.nbEdges.toString(), idNode1, idNode2);
        } catch (IdAlreadyInUseException e1) {
            System.err.println("ID existing");
            System.exit(1);
        } catch (EdgeRejectedException e2) {
            this.nbEdges--;
        } catch (ElementNotFoundException ignored) {

        }
    }

    /**
     * Compute the shortest Path from idFrom to IdTo. The computation is currently not very efficient
     *
     * @param idFrom id of the origin node
     * @param idTo   id of the destination node
     * @return the list of nodes to follow, null if the targeted node is not currently reachable
     */
    public synchronized List<String> getShortestPath(String idFrom, String idTo) {
        List<String> shortestPath = new ArrayList<String>();

        Dijkstra dijkstra = new Dijkstra();//number of edge
        dijkstra.init(g);
        dijkstra.setSource(g.getNode(idFrom));
        dijkstra.compute();//compute the distance to all nodes from idFrom
        List<Node> path = dijkstra.getPath(g.getNode(idTo)).getNodePath(); //the shortest path from idFrom to idTo
        for (Node edges : path) {
            shortestPath.add(edges.getId());
        }
        dijkstra.clear();
        if (shortestPath.isEmpty()) {//The openNode is not currently reachable
            return null;
        } else {
            shortestPath.remove(0);//remove the current position
        }
        return shortestPath;
    }

    public List<String> getShortestPathToClosestOpenNode(String myPosition) {
        //1) Get all openNodes
        List<String> opennodes = getOpenNodes();

        //2) select the closest one
        List<Couple<String, Integer>> lc =
                opennodes.stream()
                        .map(on -> (getShortestPath(myPosition, on) != null) ? new Couple<String, Integer>(on, getShortestPath(myPosition, on).size()) : new Couple<String, Integer>(on, Integer.MAX_VALUE))//some nodes my be unreachable if the agents do not share at least one common node.
                        .collect(Collectors.toList());

        Optional<Couple<String, Integer>> closest = lc.stream().min(Comparator.comparing(Couple::getRight));
        //3) Compute shorterPath

        return getShortestPath(myPosition, closest.get().getLeft());
    }

    public List<String> getPathBack(String myPosition, Integer degreeMin){

        Node root = this.g.getNode(myPosition);
        List<Node> pile = new ArrayList<>();
        pile.add(root);
        Node noeud = pile.get(0);
        Integer max = 0;
        while( !noeud.equals(null) ){
            pile.remove(noeud);
            max = noeud.getDegree();
            if (max > degreeMin && !root.equals(noeud)){
                break;
            }
            Iterator<Edge> iterE = this.g.edges().iterator();
            while (iterE.hasNext()) {
                Edge e = iterE.next();
                Node sn = e.getSourceNode();
                Node tn = e.getTargetNode();
                if(sn == noeud || tn == noeud){
                    if (!pile.contains(tn)){
                        pile.add(tn);
                    }else if (!pile.contains(sn)){
                        pile.add(sn);
                    }
                }
            }
            noeud = pile.get(0);
        }
        return this.getShortestPath(myPosition, noeud.getId());
    }

    public List<String> getPathBack(String myPosition, Integer degreeMin, String notNode){
        Node root = this.g.getNode(myPosition);
        List<Node> pile = new ArrayList<>();
        pile.add(root);
        Node noeud = pile.get(0);
        Integer max = 0;
        while( !noeud.equals(null) ){
            pile.remove(noeud);
            max = noeud.getDegree();
            if (max > degreeMin && !noeud.equals(notNode) && !root.equals(noeud)){
                break;
            }
            Iterator<Edge> iterE = this.g.edges().iterator();
            while (iterE.hasNext()) {
                Edge e = iterE.next();
                Node sn = e.getSourceNode();
                Node tn = e.getTargetNode();
                if(sn == noeud || tn == noeud){
                    if (!pile.contains(tn)){
                        pile.add(tn);
                    }else if (!pile.contains(sn)){
                        pile.add(sn);
                    }
                }
            }
            noeud = pile.get(0);
        }
        return this.getShortestPath(myPosition, noeud.getId());
    }

    public int getDistanceLeaf(String myPosition){
        Node root = this.g.getNode(myPosition);
        List<Node> pile = new ArrayList<>();
        pile.add(root);
        Node nodeFeuille=null;
        Node noeud = pile.get(0);
        while( !noeud.equals(null) ){
            pile.remove(noeud);
            int fils = 0;
            Iterator<Edge> iterE = this.g.edges().iterator();
            while (iterE.hasNext()) {
                Edge e = iterE.next();
                Node sn = e.getSourceNode();
                Node tn = e.getTargetNode();
                if(sn == noeud || tn == noeud){
                    if (!pile.contains(tn)){
                        fils ++;
                        pile.add(tn);
                    }else if (!pile.contains(sn)){
                        pile.add(sn);
                        fils ++;
                    }
                }
            }
            if(fils==0){
                nodeFeuille=noeud;
                break;
            }
            noeud = pile.get(0);
        }

        if(!nodeFeuille.equals(null)){
            List<String> chemin = this.getShortestPath(myPosition, nodeFeuille.getId());
            if(chemin.isEmpty()){
                return 0;
            }
            return chemin.size();
        }
        return -1;
    }


    public List<String> getOpenNodes() {
        return this.g.nodes()
                .filter(x -> x.getAttribute("ui.class").equals(MapAttribute.open.toString()))
                .map(Node::getId)
                .collect(Collectors.toList());
    }

    public List<String> getClosedNodes() {
        return this.g.nodes()
                .filter(x -> x.getAttribute("ui.class").equals(MapAttribute.closed.toString()))
                .map(Node::getId)
                .collect(Collectors.toList());
    }


    public List<String> getAgentNodes() {
        return this.g.nodes()
                .filter(x -> x.getAttribute("ui.class").equals(MapAttribute.agent.toString()))
                .map(Node::getId)
                .collect(Collectors.toList());
    }

    public List<String> getOtherNodes() {
        List<Node> list = this.g.nodes()
                .filter(x -> ! x.getAttribute("ui.class").equals(MapAttribute.agent.toString()))
                .filter(x -> ! x.getAttribute("ui.class").equals(MapAttribute.open.toString()))
                .filter(x -> x.getAttribute("ui.class").equals(MapAttribute.closed.toString()))
                .collect(Collectors.toList());
        List<String> res = new ArrayList<>();
        for (Node node : list){
            res.add(node.getAttribute("ui.class").toString());
        }
        return res;
    }

    public List<String> getAllNodes() {
        return this.g.nodes()
                .map(Node::getId)
                .collect(Collectors.toList());
    }

    /**
     * Before the migration we kill all non serializable components and store their data in a serializable form
     */
    public void prepareMigration() {
        serializeGraphTopology();
        closeGui();
        this.g = null;
    }

    /**
     * Before sending the agent knowledge of the map it should be serialized.
     */
    private void serializeGraphTopology() {
        this.sg = new SerializableSimpleGraph<>();


        for (Node n : this.g) {    //on copie tous les noeuds du graphe
            //sg.addNode(n.getId(), MapAttribute.valueOf((String) n.getAttribute("ui.class")));
            HashMap<String, Object> map = new HashMap<>(); // map containing all the attributes of the node
            Object[] attributes = n.attributeKeys().toArray();

            //envoie attribut du noeud n dans le graphe g
            for (Object att : attributes) {
                String key = (String) att;
                map.put(key, n.getAttribute(key).toString());
            }

            //on envoie les dictionnaires des diamonds et des golds
            map.put("diamondDict", this.diamondDict);
            map.put("goldDict", this.goldDict);

            sg.addNode(n.getId(), map);
        }

        Iterator<Edge> iterE = this.g.edges().iterator();

        while (iterE.hasNext()) {    // on copie tous les arêtes du graphe
            Edge e = iterE.next();
            Node sn = e.getSourceNode();
            Node tn = e.getTargetNode();
            sg.addEdge(e.getId(), sn.getId(), tn.getId());
        }
    }

    public synchronized SerializableSimpleGraph<String, HashMap<String, Object>> getSerializableGraph() {
        serializeGraphTopology();
        return this.sg;
    }


    private void serializeGraphTopology(HashMap<String, List<Couple<Observation,Integer>>> dictBackPack) {
        this.sg = new SerializableSimpleGraph<>();

        for (Node n : this.g) {    //on copie tous les noeuds du graphe
            //sg.addNode(n.getId(), MapAttribute.valueOf((String) n.getAttribute("ui.class")));
            HashMap<String, Object> map = new HashMap<>(); // map containing all the attributes of the node
            Object[] attributes = n.attributeKeys().toArray();

            //envoie attribut du noeud n dans le graphe g
            for (Object att : attributes) {
                String key = (String) att;
                map.put(key, n.getAttribute(key).toString());
                map.put("diamondDict", this.diamondDict);
                map.put("goldDict", this.goldDict);
                map.put("dictBackPack", dictBackPack);
            }
            sg.addNode(n.getId(), map);
        }
        //sg.addNode("diamondDict", this.diamondDict);
        //sg.addNode("goldDict", this.goldDict);
        //sg.addNode("dictBackPack", dictBackPack );

        Iterator<Edge> iterE = this.g.edges().iterator();

        while (iterE.hasNext()) {    // on copie tous les arêtes du graphe
            Edge e = iterE.next();
            Node sn = e.getSourceNode();
            Node tn = e.getTargetNode();
            sg.addEdge(e.getId(), sn.getId(), tn.getId());
        }
    }

    public synchronized SerializableSimpleGraph<String, HashMap<String, Object>> getSerializableGraph(HashMap<String, List<Couple<Observation,Integer>>> dictBackPack) {
        serializeGraphTopology(dictBackPack);
        return this.sg;
    }


    /**
     * After migration we load the serialized data and recreate the non serializable components (Gui,..)
     */
    public synchronized void loadSavedData() {
        //closeGui();

        this.g = new SingleGraph("My world vision");
        this.g.setAttribute("ui.stylesheet", nodeStyle);

        //openGui();

        int nbEd = 0;
        int nbNo = 0;
        for (SerializableNode<String, HashMap<String, Object>> n : this.sg.getAllNodes()) {
            this.g.addNode(n.getNodeId()).setAttribute("ui.class", n.getNodeContent().toString());
            nbNo++;
            for (String s : this.sg.getEdges(n.getNodeId())) {
                this.g.addEdge(Integer.toString(nbEd), n.getNodeId(), s);
                nbEd++;
            }
        }
        this.nbEdges = nbEd;
        this.nbNodes = nbNo;
        System.out.println("Loading done");
    }

    public synchronized void transformSerializabletoMAP(SerializableSimpleGraph<String, HashMap<String, Object>> sgreceived) {
        //closeGui();
        this.g = new SingleGraph("My world vision");
        this.g.setAttribute("ui.stylesheet", nodeStyle);

        //openGui();

        int nbEd = 0;
        int nbNo = 0;
        for (SerializableNode<String, HashMap<String, Object>> n : sgreceived.getAllNodes()) {
            this.g.addNode(n.getNodeId()).setAttribute("ui.class", n.getNodeContent().toString());
            nbNo++;
            for (String s : sgreceived.getEdges(n.getNodeId())) {
                this.g.addEdge(Integer.toString(nbEd), n.getNodeId(), s);
                nbEd++;
            }
        }
        this.nbEdges = nbEd;
        this.nbNodes = nbNo;
        System.out.println("Loading done");
    }

    /**
     * Method called before migration to kill all non serializable graphStream components
     */
    private synchronized void closeGui() {
        //once the graph is saved, clear non serializable components
        if (this.viewer != null) {
            //Platform.runLater(() -> {
            try {
                this.viewer.close();
            } catch (NullPointerException e) {
                System.err.println("Bug graphstream viewer.close() work-around - https://github.com/graphstream/gs-core/issues/150");
            }
            //});
            this.viewer = null;
        }
    }

    /**
     * Method called after a migration to reopen GUI components
     */
    private synchronized void openGui() {
        this.viewer = new FxViewer(this.g, FxViewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);//GRAPH_IN_GUI_THREAD)
        viewer.enableAutoLayout();
        viewer.setCloseFramePolicy(FxViewer.CloseFramePolicy.CLOSE_VIEWER);
        viewer.addDefaultView(true);

        g.display();
    }

    public void mergeMap(SerializableSimpleGraph<String, HashMap<String, Object>> sgreceived) {
        //System.out.println("You should decide what you want to save and how");
        //System.out.println("We currently blindy add the topology");
        boolean envoyeDict = true;
        // add node
        for (SerializableNode<String, HashMap<String, Object>> nReceived : sgreceived.getAllNodes()) {
            String nodeID = nReceived.getNodeId();
            HashMap<String, Object> nReceivedAttributes = nReceived.getNodeContent();
            Node nActual = this.g.getNode(nodeID);

            //dico diamond et gold
            //on envoit les dico diamond et gold dans le premier noeud (et pas aux autres)
            if (envoyeDict) {
                envoyeDict = false;
                this.updateDiamondDictExplo((HashMap<String,Couple<Integer, String>>)nReceivedAttributes.get("diamondDict"));
                this.updateGoldDictExplo((HashMap<String,Couple<Integer, String>>)nReceivedAttributes.get("goldDict"));
            }

            if (nActual == null) { // le noeud reçu n'est pas dans le graphe actuel, alors on le crée
                nActual = this.g.addNode(nodeID);
                // ajout des attributs du noeud reçu
                nActual.setAttribute("ui.label", nReceivedAttributes.get("ui.label").toString());
                nActual.setAttribute("ui.class", nReceivedAttributes.get("ui.class").toString());
                nActual.setAttribute("timestamp", nReceivedAttributes.get("timestamp").toString());
            }

            // mise à jour des attributs du noeud actuel
            for (String key: nReceivedAttributes.keySet()) {
                if (Objects.equals(key, "timestamp")) {
                    // prendre le timestamp le plus récent
                    int comparaison = (nReceived.getNodeContent().get("timestamp").toString()).compareTo(nActual.getAttribute("timestamp").toString());
                    if (comparaison >= 0) {
                        nActual.setAttribute(key, nReceivedAttributes.get(key).toString());
                    }
                }else if (Objects.equals(key, "ui.class")){
                    // mettre le noeud actuel fermé s'il l'était déjà ou si le noeud reçu est fermé
                    if (nActual.getAttribute(key).equals(MapAttribute.closed.toString()) || nReceivedAttributes.get(key).toString().equals(MapAttribute.closed.toString()) ) {
                        nActual.setAttribute(key, MapAttribute.closed.toString());
                    }
                }
                else {
                    nActual.setAttribute(key, nReceivedAttributes.get(key).toString());
                }
            }
        }

        //now that all nodes are added, we can add edges
        for (SerializableNode<String, HashMap<String, Object>> nReceived: sgreceived.getAllNodes()) {
            for (String s : sgreceived.getEdges(nReceived.getNodeId())) {
                addEdge(nReceived.getNodeId(), s);
            }
        }

        System.out.println("Merge done");
    }


    public HashMap<String, List<Couple<Observation,Integer>>> mergeMapDict(SerializableSimpleGraph<String, HashMap<String, Object>> sgreceived) {
        //System.out.println("You should decide what you want to save and how");
        //System.out.println("We currently blindy add the topology");
        boolean envoyeDict = true;
        HashMap<String, List<Couple<Observation,Integer>>> dictBackPack = null ;
        // add node
        for (SerializableNode<String, HashMap<String, Object>> nReceived : sgreceived.getAllNodes()) {
            String nodeID = nReceived.getNodeId();
            HashMap<String, Object> nReceivedAttributes = nReceived.getNodeContent();
            Node nActual = this.g.getNode(nodeID);

            //if (nodeID=="dictBackPack"){
            //   dictBackPack = nReceived.getNodeContent();
            //}

            if (nActual == null) { // le noeud reçu n'est pas dans le graphe actuel, alors on le crée
                nActual = this.g.addNode(nodeID);
                // ajout des attributs du noeud reçu
                nActual.setAttribute("ui.label", nReceivedAttributes.get("ui.label").toString());
                nActual.setAttribute("ui.class", nReceivedAttributes.get("ui.class").toString());
                nActual.setAttribute("timestamp", nReceivedAttributes.get("timestamp").toString());
            }
            // mise à jour des attributs du noeud actuel
            for (String key: nReceivedAttributes.keySet()) {
                if (Objects.equals(key, "dictBackPack") || Objects.equals(key, "diamondDict") || Objects.equals(key, "goldDict")) {
                    if (envoyeDict) {
                        envoyeDict = false;
                        dictBackPack = (HashMap<String, List<Couple<Observation,Integer>>>) nReceivedAttributes.get("dictBackPack");
                        this.updateDiamondDictExplo((HashMap<String,Couple<Integer, String>>)nReceivedAttributes.get("diamondDict"));
                        this.updateGoldDictExplo((HashMap<String,Couple<Integer, String>>)nReceivedAttributes.get("goldDict"));
                    }
                }else if (Objects.equals(key, "timestamp")) {
                    // prendre le timestamp le plus récent
                    int comparaison = (nReceived.getNodeContent().get("timestamp").toString()).compareTo(nActual.getAttribute("timestamp").toString());
                    if (comparaison >= 0) {
                        nActual.setAttribute(key, nReceivedAttributes.get(key).toString());
                    }
                }else if (Objects.equals(key, "ui.class")){
                    // mettre le noeud actuel fermé s'il l'était déjà ou si le noeud reçu est fermé
                    if (nActual.getAttribute(key).equals(MapAttribute.closed.toString()) || nReceivedAttributes.get(key).toString().equals(MapAttribute.closed.toString()) ) {
                        nActual.setAttribute(key, MapAttribute.closed.toString());
                    }
                }
                else {
                    nActual.setAttribute(key, nReceivedAttributes.get(key).toString());
                }
            }
        }

        //now that all nodes are added, we can add edges
        for (SerializableNode<String, HashMap<String, Object>> nReceived: sgreceived.getAllNodes()) {
            for (String s : sgreceived.getEdges(nReceived.getNodeId())) {
                addEdge(nReceived.getNodeId(), s);
            }
        }
        System.out.println("Merge done");
        return dictBackPack;
    }

    /**
     * @return true if there exist at least one openNode on the graph
     */
    public boolean hasOpenNode() {
        return (this.g.nodes()
                .anyMatch(n -> n.getAttribute("ui.class").equals(MapAttribute.open.toString())));
    }

    /*--------------------- Version optimisé ---------------------------*/

    /*
    private void serializeGraphTopologyOptimum(SerializableSimpleGraph<String, HashMap<String, Object>> sgReceived) {
        // Retourne les noeuds et les aretes qui ne sont pas presents dans sgReceived mais present dans g
        this.sg = new SerializableSimpleGraph<String, HashMap<String, Object>>();
        Iterator<Node> nodeSend = this.g.iterator();
        while (nodeSend.hasNext()) {    //on copie tous les noeuds du graphe
            Node node_g = nodeSend.next();

            //On vérifie si le noeud n (qui est dans le graphe nommé g) est présent dans le graphe nommé sgReceived (le graphe d'un autre agent)
            // 		si oui, on fait rien
            // 		sinon, on ajoute le noeud n (et ses arcs) dans le graphe nommé sg (qui est le graphe qu'on envoit par message)
            SerializableNode<String, HashMap<String, Object>> node_sgR = sgReceived.getNode(node_g.getId());

            if (node_sgR == null) { //on n'a pas trouver le noeud node_g dans sgReceived

                //Ajout du noeud node_g dans le graphe nommé sg
                sg.addNode(node_g.getId(), MapAttribute.valueOf((String) node_g.getAttribute("ui.class")));

                //Ajout des arcs de node_g dans le graphe nommé sg
                Set<Edge> edge_g = (Set<Edge>) node_g.edges(); //ensemble d'arc de node_g
                for (Edge e : edge_g) {
                    Node sn = e.getSourceNode();
                    Node tn = e.getTargetNode();

                    if (sn != node_g) {
                        sg.addEdge(e.getId(), tn.getId(), sn.getId());
                    } else {
                        sg.addEdge(e.getId(), sn.getId(), tn.getId());
                    }
                }
            }
        }
        //System.out.println("FIN : serializeGraphTopologyOptimum"+this.sg.getAllNodes());
    }


    public synchronized SerializableSimpleGraph<String, HashMap<String, Object>> getSerializableGraphOptimum(SerializableSimpleGraph<String, HashMap<String, Object>> sgReceived) {
        serializeGraphTopologyOptimum(sgReceived);
        return this.sg;
    }
    */

}