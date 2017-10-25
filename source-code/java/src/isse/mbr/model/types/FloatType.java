package isse.mbr.model.types;

import isse.mbr.model.parsetree.AbstractPVSInstance;

public class FloatType implements PrimitiveType {

	@Override
	public String toString() {
		return "float";
	}
	
	@Override
	public boolean isFloat() {
		return true;
	}

	@Override
	public String toMzn(AbstractPVSInstance instance) {
		return "float";
	}
}
