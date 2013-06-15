package yuval.neo4j.deltahyperbolic;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.index.IndexService;
import org.neo4j.util.GraphDatabaseLifecycle;

/**
 * Wrapper around Neo4j every node has a name. Nodes are created lazily when
 * relationships are created.
 * 
 * @author Anders Nawroth
 */
public class ExampleGraphService
{
    public enum MyDijkstraTypes implements RelationshipType
    {
        REL
    }

    public static final String NAME = "name";

    private final GraphDatabaseService graphDb;
    private final GraphDatabaseLifecycle lifecycle;
    private final Index<Node> index;

    /**
     * Create new or open existing DB.
     * 
     * @param storeDir location of DB files
     */
    public ExampleGraphService( final String storeDir )
    {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(storeDir);
        lifecycle = new GraphDatabaseLifecycle( graphDb );
        index = graphDb.index().forNodes("nodes");
    }

    /**
     * Create relationship between two nodes and set a property on the
     * relationship. Note that the propertyValue has to be a Java primitive or
     * String or an array of either Java primitives or Strings.
     * 
     * @param fromNodeName start node
     * @param toNodeName end node
     * @param propertyName
     * @param propertyValue
     */
    public void createRelationship( final String fromNodeName,
            final String toNodeName)
    {
        Transaction tx = graphDb.beginTx();

        try
        {
            // find/create nodes
            Node firstNode = findOrCreateNode( fromNodeName );
            Node secondNode = findOrCreateNode( toNodeName );

            // add relationship
            firstNode.createRelationshipTo( secondNode, MyDijkstraTypes.REL );

            tx.success();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        finally
        {
            tx.finish();
        }
    }

    /**
     * Get a node by its name.
     * 
     * @param name
     * @return
     */
    public Node getNode( final String name )
    {
        return index.get( NAME, name ).getSingle();
    }

    /**
     * Find a node or create a new node if it doesn't exist.
     * 
     * @param nodeName
     * @return
     */
    private Node findOrCreateNode( final String nodeName )
    {
        Node node = getNode( nodeName );
        if ( node == null )
        {
            node = graphDb.createNode();
            node.setProperty( NAME, nodeName );
            index.add(node, NAME, node.getProperty(NAME));
        }
        return node;
    }

    /**
     * Shutdown service.
     */
    public void shutdown()
    {
        graphDb.shutdown();
        lifecycle.manualShutdown();
    }
}