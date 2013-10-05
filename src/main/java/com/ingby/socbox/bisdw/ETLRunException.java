package com.ingby.socbox.bisdw;

public class ETLRunException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -9107510851837346937L;

	public ETLRunException() {
		super();
	}

	public ETLRunException(String message) {
		super(message);
	}

	public ETLRunException(String message,Throwable cause) {
		super(message, cause);
	}

	public ETLRunException(Throwable cause) {
		super(cause);
	}
}
