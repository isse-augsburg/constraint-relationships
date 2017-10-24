package isse.mbr.model.parsetree;

import java.util.Collection;

/**
 * Abstract PVS instance might be a specific (atomic) PVS instance as well
 * as a complex one (composed by product)
 * @author Alexander Schiendorfer
 *
 */
public abstract class AbstractPVSInstance {
	protected String name;
	protected String generatedBetterPredicate;
	protected String generatedNotWorsePredicate;
	protected Collection<AbstractPVSInstance> children;
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getGeneratedBetterPredicate() {
		return generatedBetterPredicate;
	}

	public void setGeneratedBetterPredicate(String generatedBetterPredicate) {
		this.generatedBetterPredicate = generatedBetterPredicate;
	}

	public String getGeneratedNotWorsePredicate() {
		return generatedNotWorsePredicate;
	}

	public void setGeneratedNotWorsePredicate(String generatedNotWorsePredicate) {
		this.generatedNotWorsePredicate = generatedNotWorsePredicate;
	}

	public abstract boolean isComplex();

	// this should basically be a read-only property
	public abstract Collection<AbstractPVSInstance> getChildren();
	
}
