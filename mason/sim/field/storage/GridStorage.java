package sim.field.storage;

import java.io.Serializable;
import java.util.stream.IntStream;

import mpi.*;

import sim.field.HaloField;
import sim.util.IntHyperRect;
import sim.util.IntPoint;
import sim.util.MPIParam;

public abstract class GridStorage {
	Object storage;
	IntHyperRect shape;
	Datatype baseType = MPI.BYTE;

	int[] stride;

	public GridStorage(IntHyperRect shape) {
		this.shape = shape;
		this.stride = getStride(shape.getSize());
	}

	public Object getStorage() {
		return storage;
	}

	public Datatype getMPIBaseType() {
		return baseType;
	}

	public IntHyperRect getShape() {
		return shape;
	}

	public abstract String toString();
	public abstract Serializable pack(MPIParam mp) throws MPIException;
	public abstract int unpack(MPIParam mp, Serializable buf) throws MPIException;
	
	// Method that allocates an array of objects of desired type
	// This method will be called after the new shape has been set
	protected abstract Object allocate(int size);

	private void reload(IntHyperRect newShape) {
		this.shape = newShape;
		this.stride = getStride(newShape.getSize());
		this.storage = allocate(newShape.getArea());
	}

	public void reshape(IntHyperRect newShape) {
		if (newShape.equals(shape))
			return;
		
		if (newShape.isIntersect(shape)) {
			IntHyperRect overlap = newShape.getIntersection(shape);
			MPIParam fromParam = new MPIParam(overlap, shape, baseType);
			MPIParam toParam = new MPIParam(overlap, newShape, baseType);

			try {
				Serializable buf = pack(fromParam);
				reload(newShape);
				unpack(toParam, buf);

				fromParam.type.free();
				toParam.type.free();
			} catch (MPIException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		} else
			reload(newShape);
	}

	public int getFlatIdx(IntPoint p) {
		return IntStream.range(0, p.nd).map(i -> p.c[i] * stride[i]).sum();
	}

	// Get the flatted index with respect to the given size
	public static int getFlatIdx(IntPoint p, int[] wrtSize) {
		int[] s = getStride(wrtSize);
		return IntStream.range(0, p.nd).map(i -> p.c[i] * s[i]).sum();
	}

	protected static int[] getStride(int[] size) {
		int[] ret = new int[size.length];

		ret[size.length - 1] = 1;
		for (int i = size.length - 2; i >= 0; i--)
			ret[i] = ret[i + 1] * size[i + 1];

		return ret;
	}
}