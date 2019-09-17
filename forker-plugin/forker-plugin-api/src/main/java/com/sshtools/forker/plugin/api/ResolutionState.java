package com.sshtools.forker.plugin.api;

public enum ResolutionState {
	ERRORED, STARTED, RESOLVED, SATISFACTORY, PARTIAL, UNRESOLVED;
	
	public ResolutionState best(ResolutionState other) {
		if(other.ordinal() < ordinal())
			return other;
		else
			return this;
	}

	public ResolutionState least(ResolutionState other) {
		if(other.ordinal() > ordinal())
			return other;
		else
			return this;
	}

	public boolean isUsable() {
		switch (this) {
		case RESOLVED:
		case STARTED:
		case ERRORED:
		case SATISFACTORY:
			return true;
		default:
			return false;
		}
	}
	
	public boolean isComplete() {
		return this == RESOLVED || this == STARTED;
	}

	public boolean isResolved() {
		switch (this) {
		case RESOLVED:
		case STARTED:
		case PARTIAL:
		case ERRORED:
		case SATISFACTORY:
			return true;
		default:
			return false;
		}
	}
}
