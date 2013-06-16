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
			br = new BufferedReader(new FileReader ("input_graphs/Email-Enron.txt"));
			String line = br.readLine();
			int lineCount = 1;
			
			while (line != null)
			{
				System.out.println("Creating line "+ lineCount + " : " + line);
				String[] nodes = line.split("\t");
				graph.createRelationship(nodes[0], nodes[1]);		
				line = br.readLine();
				++lineCount;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			br.close();
		}

	}

	/**
	 * Find the path.
	 */
	private int findShortestPath(Node start, Node end)
	{
		PathFinder<Path> finder = GraphAlgoFactory.shortestPath(expander, 36691);

		return finder.findSinglePath(start, end).length();        
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
			final int dTotal = 36691;
			int dIterations = 10*(int) Math.log(dTotal);
			double maxDelta = 0;
			TraversalDescription td = new TraversalDescriptionImpl();


			System.out.println("Starting samples - number of iteration " + dIterations);
			int minPosition = 0;//(dTotal/2 - 2*(int)Math.log(dTotal))/2;
			int maxPosition = 0;//(dTotal/2 + 2*(int)Math.log(dTotal))/2;

			for(int i=0; i<dIterations; ++i)
			{
				int aIndex = (int) (Math.ceil(Math.random() * dTotal));
				Node a = de.graph.getNode(Integer.toString(aIndex));

				Traverser bfsTraverser = td.breadthFirst().traverse(a);

				Vector<Node> cAndD = new Vector<Node>();
				Vector<Node> bNodes = new Vector<Node>();
				int counter  = 0;
				int size = 0;

				Iterator<Node> aIterator = bfsTraverser.nodes().iterator();
				while (aIterator.hasNext())
				{
					++size;
					aIterator.next();
				}
				
				System.out.println(i + " BFS size " + size); 
				if (size < (int)Math.sqrt(dTotal))
				{
					System.out.println("Too small, jumping...");
					continue;
				}
				
				minPosition = (size/2 - 4*(int)Math.log(size))/2;
				maxPosition = (size/2 + 4*(int)Math.log(size))/2;

				for (Node node : bfsTraverser.nodes())
				{

					if (counter > minPosition && counter < maxPosition)
					{
						cAndD.add(node);
					}

					if ((size > 4 && (size-4 == counter)) || (size > 3 && (size -3 == counter)) || (size > 2 && (size-2 == counter)))
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
							else if(d2< d3 && d2<d3)
								intermediateDelta = ((double)Math.abs(d1-d3))/2;
							else
								intermediateDelta = ((double)Math.abs(d1-d2))/2;

							if(maxDelta < intermediateDelta)
							{
								System.out.println();
								System.out.println("New max delta " + intermediateDelta);
								maxDelta = intermediateDelta;
							}
						}
					}
				}
			}
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