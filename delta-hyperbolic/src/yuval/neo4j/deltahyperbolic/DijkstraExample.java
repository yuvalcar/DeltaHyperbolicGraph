package yuval.neo4j.deltahyperbolic;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Time;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.lucene.search.FieldComparator.RelevanceComparator;
import org.apache.lucene.search.TimeLimitingCollector.TimerThread;
import org.neo4j.graphalgo.CommonEvaluators;
import org.neo4j.graphalgo.CostEvaluator;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.kernel.StandardExpander;
import org.neo4j.kernel.Traversal;
import org.neo4j.kernel.impl.traversal.TraversalDescriptionImpl;

import scala.collection.immutable.VectorIterator;

/**
 * Simple example of how to find the cheapest path between two nodes in a graph.
 * 
 * @author Anders Nawroth
 */
public class DijkstraExample
{
	public static final int GraphSize = 36691;
	public static final String GraphName = "input_graphs/Email-Enron.txt";
	public final ExampleGraphService graph;

	private static final RelationshipExpander expander;

	static
	{
		// set up path finder
		expander = Traversal.expanderForTypes(
				ExampleGraphService.MyDijkstraTypes.REL, Direction.BOTH );
	}

	public DijkstraExample()
	{
		graph = new ExampleGraphService( "target/neo4j" );
	}

	/**
	 * Create our example graph.
	 *@throws IOException
	 */
	private void createGraph() throws IOException
	{	
		BufferedReader br = null;

		try {
			System.out.println("Parsing graph");
			br = new BufferedReader(new FileReader (GraphName));
			String line = br.readLine();
			int lineCount = 1;

			while (line != null)
			{
				//System.out.print(".");
				if (lineCount % 1000 == 0)
					System.out.println("Creating line "+ lineCount);
				String[] nodes = line.split("\t");
				graph.createRelationship(nodes[0], nodes[1]);		
				line = br.readLine();
				++lineCount;
			}
			System.out.println();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			br.close();
		}

	}


	public void printGraph()
	{
		for (int i = 1; i<=GraphSize; ++i)
		{
			Node iNode = graph.getNode(Integer.toString(i));
			System.out.print("Node " + i);
			for (Relationship j : iNode.getRelationships())
			{
				for (Node k : j.getNodes())
				{
					System.out.print("->" + k.toString());
				}

				System.out.print(" ");
			}

			System.out.println();
		}
	}

	/**
	 * Find the path.
	 */
	private int findShortestPath(Node start, Node end)
	{
		if (start == null || end == null)
			return -1;

		PathFinder<Path> finder = GraphAlgoFactory.shortestPath(expander, GraphSize);

		Path sPath = finder.findSinglePath(start, end);

		if (null == sPath)
			return -1;
		else
			return sPath.length();        
	}

	/**
	 * Shutdown the graphdb.
	 */
	private void shutdown()
	{
		graph.shutdown();
	}

	/**
	 * Execute the example.
	 * 
	 * @param args
	 */
	public static void main( final String[] args )
	{
		System.out.println("Creating Graph...");
		DijkstraExample de = new DijkstraExample();
		try {
			de.createGraph();
			System.out.println("Done creating graph");
			//de.printGraph();

			final int dTotal = GraphSize;
			final int dTotalLog = (int)Math.log(dTotal);
			int dIterations = 10*dTotalLog;
			double maxDelta = 0;
			TraversalDescription td = new TraversalDescriptionImpl();


			System.out.println("Starting samples - number of iteration " + dIterations);
			int minPosition = 0;
			int maxPosition = 0;
			int lastBFSSize = -1;


			for(int i=0; i<dIterations; ++i)
			{
				int aIndex = (int) (Math.ceil(Math.random() * dTotal));
				Node a = de.graph.getNode(Integer.toString(aIndex));

				Traverser bfsTraverser = td.breadthFirst().traverse(a);

				Vector<Node> cAndD = new Vector<Node>();
				Vector<Node> bNodes = new Vector<Node>();
				int counter  = 0;
				int size = 0;
				int logSize = 0;

				Iterator<Node> aIterator = bfsTraverser.nodes().iterator();
				while (aIterator.hasNext())
				{
					++size;
					aIterator.next();
				}
				
				if (size != lastBFSSize)
				{
					System.out.println(i + " BFS size " + size);
					lastBFSSize = size;
					logSize = (int)Math.log(size);
					minPosition = logSize;
					maxPosition = minPosition + logSize;
				}
				
				if (size < dTotalLog)
				{
					System.out.println("Too small, jumping...");
					continue;
				}

				for (Node node : bfsTraverser.nodes())
				{

					if (counter > minPosition && counter < maxPosition)
					{
						cAndD.add(node);
					}

					if (counter > size - Math.pow(logSize, 2) && counter % logSize == 0)
						bNodes.add(node);

					++counter;
				}

				for (Iterator<Node> bIterator = bNodes.iterator(); bIterator.hasNext();)
				{
					Node b = bIterator.next();
					int ab = de.findShortestPath(a, b);
					for (Iterator<Node> cIterator = cAndD.iterator(); cIterator.hasNext();)
					{
						Node c = cIterator.next();
						for (Iterator<Node> dIterator = cAndD.iterator() ; dIterator.hasNext();)
						{
							Node d = dIterator.next();
							int ac = de.findShortestPath(a, c);
							int ad = de.findShortestPath(a, d);
							int bc = de.findShortestPath(b, c);
							int bd = de.findShortestPath(b, d);
							int cd = de.findShortestPath(c, d);

							int d1 = ab+cd;
							int d2 = ac+bd;
							int d3 = ad+bc;

							double intermediateDelta;

							if(d1< d2 && d1<d3)
								intermediateDelta = ((double)Math.abs(d2-d3))/2;
							else if(d2< d3 && d2<d1)
								intermediateDelta = ((double)Math.abs(d1-d3))/2;
							else
								intermediateDelta = ((double)Math.abs(d1-d2))/2;

							if(maxDelta < intermediateDelta)
							{
								System.out.println();
								maxDelta = intermediateDelta;
								System.out.println("Delta changed : ");
								System.out.println("a = " + a + " & b = "+ b + " & c = " + c+ " & d = " + d + " & tempDelta = " + maxDelta);
							}
						}
					}
				}
			}
/*
			final int error = -1;
			for (int a=1; a<=dTotal; ++a)
			{
				Node aNode = de.graph.getNode(Integer.toString(a));
				for (int b=a+1; b<=dTotal; ++b)
				{
					Node bNode = de.graph.getNode(Integer.toString(b));
					int ab = de.findShortestPath(aNode, bNode);
					for (int c=b+1; c<=dTotal; ++c)
					{
						Node cNode = de.graph.getNode(Integer.toString(c));
						int ac = de.findShortestPath(aNode, cNode);
						int bc = de.findShortestPath(bNode, cNode);

						for (int d=c+1; d<=dTotal; ++d)
						{
							Node dNode = de.graph.getNode(Integer.toString(d));
							int ad = de.findShortestPath(aNode, dNode);
							int bd = de.findShortestPath(bNode, dNode);
							int cd = de.findShortestPath(cNode, dNode);


							if (error == ab || error == ac || error == ad || error == bc || error == bd || error == cd)
								continue;

							int d1 = ab+cd;
							int d2 = ac+bd;
							int d3 = ad+bc;

							double intermediateDelta;

							if(d1<=d2 && d1<=d3)
								intermediateDelta = ((double)Math.abs(d2-d3))/2;
							else if(d2<=d1 && d2<=d3)
								intermediateDelta = ((double)Math.abs(d1-d3))/2;
							else
								intermediateDelta = ((double)Math.abs(d1-d2))/2;

							if(maxDelta < intermediateDelta)
							{
								maxDelta = intermediateDelta;
								System.out.println("Delta changed : ");
								System.out.println("a = " + a + " & b = "+ b + " & c = " + c+ " & d = " + d + " & tempDelta = " + maxDelta);
							}
						}
					}
				}
			}*/
			System.out.println("MaxDelta is: "+maxDelta);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//de.runDijkstraPathFinder();
		de.shutdown();
	}
}




/* brute force
for (int a=1; a<=dTotal; ++a)
{
	for (int b=a+1; b<=dTotal; ++b)
	{
		for (int c=b+1; c<=dTotal; ++c)
		{
			for (int d=c+1; d<=dTotal; ++d)
			{
				int ab = de.runDijkstraPathFinder(a, b);
				int ac = de.runDijkstraPathFinder(a, c);
				int ad = de.runDijkstraPathFinder(a, d);
				int bc = de.runDijkstraPathFinder(b, c);
				int bd = de.runDijkstraPathFinder(b, d);
				int cd = de.runDijkstraPathFinder(c, d);

				int d1 = ab+cd;
				int d2 = ac+bd;
				int d3 = ad+bc;

				double intermediateDelta;

				if(d1< d2 && d1<d3)
					intermediateDelta = ((double)Math.abs(d2-d3))/2;
				else if(d2< d3 && d2<d3)
					intermediateDelta = ((double)Math.abs(d1-d3))/2;
				else
					intermediateDelta = ((double)Math.abs(d1-d2))/2;

				if(maxDelta < intermediateDelta)
					maxDelta = intermediateDelta;

			}
		}
		System.out.println("a = " + a + " & b = "+ b + " tempDelta = " + maxDelta);
	}
}
 */