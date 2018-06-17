package sim.field;

import java.util.*;
import java.util.stream.*;

import mpi.*;

import sim.util.*;

public class DQuadTreePartition extends DPartition {
	QuadTree qt;
	QTNode myLeafNode; // the leaf node that this pid is mapped to
	Map<Integer, GroupComm> groups; // Map the level to its corresponding comm group
	int[] aoi;

	public DQuadTreePartition(int[] size, boolean isToroidal, int[] aoi) {
		super(size, isToroidal);
		this.aoi = aoi;
		qt = new QuadTree(new IntHyperRect(size), np);
	}

	public IntHyperRect getPartition() {
		return myLeafNode.getShape();
	}

	public IntHyperRect getPartition(int pid) {
		for (QTNode node : qt.getAllLeaves())
			if (node.getProc() == pid)
				return node.getShape();

		throw new IllegalArgumentException("The partition for " + pid + " does not exist");
	}

	public int getNumNeighbors() {
		return getNeighborIds().length;
	}

	public int[] getNeighborIds() {
		return qt.getNeighborPids(myLeafNode, aoi);
	}

	public int toPartitionId(IntPoint p) {
		return qt.getLeafNode(p).getProc();
	}

	protected void setMPITopo() {
		int[] ns = getNeighborIds();

		try {
			// Create a unweighted & undirected graph for neighbor communication
			comm = MPI.COMM_WORLD.createDistGraphAdjacent(
			           ns,
			           ns,
			           new Info(),
			           false
			       );

			// Create the group comms for nodes at the same level (intercomm) and for nodes and its all leaves (intracomm)
			createGroups();
		} catch (MPIException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void initQuadTree(List<IntPoint> splitPoints) {
		// Create the quad tree based on the given split points
		qt.split(splitPoints);

		// map all quad tree nodes to processors
		mapNodeToProc();
		setMPITopo();
	}

	public void initUniformly() {
		// Init into a full quad tree
		// Check whether np is power of (2 * nd)
		// np's binary represention only contains a single one i.e., (np & (np - 1)) == 0
		// and the number of zeros before it is evenly divided by nd
		int nz = 0;
		while ((np >> nz & 0x1) != 0x1)
			nz++;
		if ((np & np - 1) != 0 || nz % nd != 0)
			throw new IllegalArgumentException("Currently only support the number processors that is power of " + (2 * nd));

		for (int level = 0; level < nz / nd; level++) {
			List<QTNode> leaves = qt.getAllLeaves();
			for (QTNode leaf : leaves)
				qt.split(leaf.getShape().getCenter());
		}

		mapNodeToProc();
		setMPITopo();
	}

	protected void mapNodeToProc() {
		List<QTNode> leaves = qt.getAllLeaves();

		if (leaves.size() != np)
			throw new IllegalArgumentException("The number of leaves " + leaves.size() + " does not equal to the number of processors " + np);

		// Map the leaf nodes first
		for (int i = 0; i < np; i++)
			leaves.get(i).setProc(i);

		myLeafNode = leaves.get(pid);

		// Map non-leaf nodes - Use the first children node to hold itself
		while (leaves.size() > 0) {
			QTNode curr = leaves.remove(0), parent = curr.getParent();
			if (parent == null || parent.getChild(0) != curr)
				continue;
			parent.setProc(curr.getProc());
			leaves.add(parent);
		}

		// Set the proc id to the IntHyperRect so it can be printed out when debugging
		// it is not used by the program itself (TODO double-check)
		for (QTNode leaf : qt.getAllLeaves())
			leaf.getShape().setId(leaf.getProc());
	}

	protected void createGroups() throws MPIException {
		int currDepth = 0;
		groups = new HashMap<Integer, GroupComm>();

		// Iterate level by level to create groups
		List<QTNode> currLevel = new ArrayList<QTNode>() {{ add(qt.getRoot()); }};
		while (currLevel.size() > 0) {
			List<QTNode> nextLevel = new ArrayList<QTNode>();

			for (QTNode node : currLevel) {
				nextLevel.addAll(node.getChildren());

				// whether this pid should participate in this group
				if (node.isAncestorOf(myLeafNode))
					groups.put(currDepth, new GroupComm(node));

				// Others will wait until the group is created
				MPI.COMM_WORLD.barrier();
			}

			GroupComm gc = groups.get(currDepth);
			if (isGroupMaster(gc))
				gc.setInterComm(currLevel);

			MPI.COMM_WORLD.barrier();

			currLevel = nextLevel;
			currDepth++;
		}
	}

	// Return whether the calling pid is the master node of the given GroupComm
	public boolean isGroupMaster(GroupComm gc) {
		return gc != null && gc.master.getProc() == pid;
	}

	// return the GroupComm instance if the calling pid should be involved
	// in the group communication of the given level
	// return null otherwise
	public GroupComm getGroupComm(int level) {
		return groups.get(level);
	}

	// return the shape when the calling pid holds one of the master nodes of this level
	// return null otherwise
	public IntHyperRect getNodeShapeAtLevel(int level) {
		GroupComm gc = getGroupComm(level);
		if (isGroupMaster(gc))
			return gc.master.getShape();
		return null;
	}

	private void testIntraGroupComm(int depth) throws MPIException {
		MPITest.printOnlyIn(0, "Testing intra group comm at depth " + depth);

		if (groups.containsKey(depth)) {
			Comm gcomm = groups.get(depth).comm;
			int[] buf = new int[16];

			buf[gcomm.getRank()] = pid;
			gcomm.allGather(buf, 1, MPI.INT);
			System.out.println(String.format("PID %2d %s", pid, Arrays.toString(buf)));
		}

		MPI.COMM_WORLD.barrier();
	}

	private void testInterGroupComm(int depth) throws MPIException {
		MPITest.printOnlyIn(0, "Testing inter group comm at depth " + depth);

		GroupComm gc = getGroupComm(depth);
		if (isGroupMaster(gc)) {
			Comm gcomm = gc.interComm;
			int[] buf = new int[16];

			buf[gcomm.getRank()] = pid;
			gcomm.allGather(buf, 1, MPI.INT);
			System.out.print(String.format("PID %2d %s\n", pid, Arrays.toString(buf)));
		}

		MPI.COMM_WORLD.barrier();
	}

	public static void main(String[] args) throws MPIException {
		MPI.Init(args);

		DQuadTreePartition p = new DQuadTreePartition(new int[] {100, 100}, false, new int[] {1, 1});

		IntPoint[] splitPoints = new IntPoint[] {
		    new IntPoint(50, 50),
		    new IntPoint(25, 25),
		    new IntPoint(75, 75),
		    new IntPoint(60, 90),
		    new IntPoint(10, 10)
		};

		p.initQuadTree(Arrays.asList(splitPoints));
		//p.initUniformly();

		for (int i = 0; i < 3; i++) {
			p.testIntraGroupComm(i);
			p.testInterGroupComm(i);
		}

		MPI.Finalize();
	}
}