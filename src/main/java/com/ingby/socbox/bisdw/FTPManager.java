package com.ingby.socbox.bisdw;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.util.Properties;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;


/**
 * The FTPManager ftp connections. A manager object is created by a property 
 * list that control the connection.
 * The usage is basically the following:<p>
 * <code>
 * FTPManager ftp = new FTPManager(properties);<br>
 * try {<br>
 * &nbsp;&nbsp;ftp.init();<br>
 * &nbsp;&nbsp;ftp.putAllDirectory();<br>
 * } catch (IOException e) {<br>
 * &nbsp;&nbsp;......<br>
 * } catch(FTPManagerException e) {<br>
 * &nbsp;&nbsp;.....	<br>
 * } finally {<br>
 * &nbsp;&nbsp;ftp.disconnect();<br>
 * }<br>
 * </code> 
 * <p>
 * The following properties are supported:<br>
 * <ul>
 * <li><i>hostname</i> - the name or IP of the ftp server to connect to.</li>
 * <li><i>port</i> - the socket port used by the ftp server, default is 21.</li>
 * <li><i>timeout</i> - connection timeout, default is 2000 ms.</li>
 * <li><i>username</i> - username for the ftp server.</li>
 * <li><i>password</i> - password for the username</li>
 * <li><i>transferMode</i> - ascii or binary, default is ascii</li>
 * <li><i>connectionMode</i> - active or passive, default is passive</li>
 * <li><i>remoteDir</i> - the directory on ftp server side, default is the current directory after login.</li>
 * <li><i>localDir</i> - the directory on the local side.</li>
 * <li><i>moveFileAfterSend</i> - move the files after transfer to the directory .save in the 
 * localDir default is true. If set to false the file is just deleted.</li>
 * </ul>
 */
public class FTPManager {

	private static final Logger LOGGER = Logger.getLogger(FTPManager.class);
	private static final String SAVEDIRECTORY = ".save";

	private FTPClient ftp;
	
	private String hostName;
	private int port;
	private int connectTimeout;
	private String username;
	private String password;
	private String transferMode;
	private String connectionMode;
	
	private Boolean moveFileAfterSend;
	private String remoteDir;
	private String localDir;
	
	private File localDirFile;
	
	
	public FTPManager(Properties properties) {
		ftp = new FTPClient();
		hostName          = properties.getProperty("hostname");
		connectTimeout    = Integer.parseInt(properties.getProperty("timeout", "2000"));
		port              = Integer.parseInt(properties.getProperty("port", "21"));
		username          = properties.getProperty("username");
		password          = properties.getProperty("password");
		transferMode      = properties.getProperty("transferMode", "ascii");
		connectionMode    = properties.getProperty("connectionMode","passive");
		remoteDir         = properties.getProperty("remoteDir");
		localDir          = properties.getProperty("localDir");
		moveFileAfterSend = Boolean.valueOf(properties.getProperty("moveDir","true"));
	}
	
	/**
	 * Initialize the ftp connection. This include connecting, login, 
	 * setting connection modes, change remote and local directory.
	 * @throws FTPManagerException if any of the initialization do not work
	 */
	public void init() throws FTPManagerException{
		try {
			connect();
			login();
            transferMode();
            connectionMode();
            setRemoteDirectory();            
            localDirFile = setLocalDirectory();
            if(LOGGER.isInfoEnabled()) {
        		LOGGER.info(ftpInfo().toString());
            }
			
		} catch (IOException e) {
			LOGGER.error("Init failed with exception",e);
			try {
                ftp.disconnect();
            } catch (IOException e1) {
                LOGGER.error("Unable to disconnect from FTP server " +
                                   "after server refused connection",e1);
            }
			throw new FTPManagerException(e);
		} 
	}
	
	/**
	 * Disconnect from the ftp server.
	 */
	public void disconnect() {
		try {
            ftp.disconnect();
        } catch (IOException e1) {
            LOGGER.error("Unable to disconnect from FTP server.", e1);
        }
	}
	

	/**
	 * Send all files in the localDir to the remoteDir on the ftp server.
	 * @throws IOException occur if the local file do not exists, if there is 
	 * IO related issues to the transfer or if the local file can not be copied
	 * to the .save directory. All scenarios are logged on ERROR level.
	 */
	public void putAllDirectory() throws IOException {
		
		int reply;
		File[] listOfFiles = localDirFile.listFiles(); 
		int count = 0;
		
		for (int i = 0; i < listOfFiles.length; i++) {

			if (listOfFiles[i].isFile()) {
				File file = listOfFiles[i];
				if (LOGGER.isInfoEnabled()) {
					if (remoteDir == null) {
						LOGGER.info(file.getAbsoluteFile() + 
								" => " + 
								ftp.getRemoteAddress().getHostAddress() + 
								" => Dir: (default) File: "+ 
								file.getName());
					} else {
						LOGGER.info(file.getAbsoluteFile() + 
								" => " + 
								ftp.getRemoteAddress().getHostAddress() + 
								" => Dir: (" + 
								remoteDir +") File: "+ 
								file.getName());
					}
				}
				
				boolean sendStatus = false;
				try {
					sendStatus = ftp.storeFile(file.getName(), new FileInputStream(file.getAbsoluteFile()));
					count++;
				} catch (FileNotFoundException e) {
					LOGGER.error("File " + file.getAbsoluteFile() + " was not found",e);
					throw e;
				} catch (IOException e) {
					LOGGER.error("Ftp file " + file.getAbsoluteFile() + " failed", e);
					throw e;
				}
				
				if (!sendStatus) {
					reply = ftp.getReplyCode();
		            if(!FTPReply.isPositiveCompletion(reply)) {
		            	LOGGER.error("Put error " + ftp.getReplyString());
		            }
				} else {
					if (moveFileAfterSend) {
						File moveDir = new File(localDir,SAVEDIRECTORY);

						if (!moveDir.exists()) {
							moveDir.mkdir();
						} 
						
						try {
							copyFile(file, new File(moveDir,file.getName()));
							file.delete();
						} catch (IOException e) {
							LOGGER.error("Move send file to " + moveDir.getAbsolutePath() + " failed", e);
							throw e;
						}
					} else {
						file.delete();
					}
				}
			}
		}
		
		LOGGER.info(count + " files sent to " + ftp.getRemoteAddress().getHostAddress());
	}


	private StringBuffer ftpInfo() throws IOException {
		StringBuffer strbuf = new StringBuffer();
		strbuf.append("{");
		strbuf.append("server:").append(ftp.getRemoteAddress().getHostAddress()).append(", ");
		strbuf.append("connectionMode:").append(ftp.getDataConnectionMode()).append(", ");
		strbuf.append("connectionModeSet:").append(connectionMode).append(", ");
		strbuf.append("transferMode:").append(transferMode).append(", ");
		strbuf.append("ctrlenc:").append(ftp.getControlEncoding()).append(", ");
		strbuf.append("systemtype:").append(ftp.getSystemType()).append(", ");
		strbuf.append("charset:").append(ftp.getCharset().name());
		strbuf.append("}");
		return strbuf;
	}

	
	
	private void copyFile(File sourceFile, File destFile) throws IOException {
	    if(!destFile.exists()) {
	        destFile.createNewFile();
	    }
	    FileInputStream sourceStream = null;
	    FileOutputStream destStream = null;
	    FileChannel source = null;
	    FileChannel destination = null;
	    try {
	    	sourceStream = new FileInputStream(sourceFile);
	        source = sourceStream.getChannel();

	        destStream = new FileOutputStream(destFile);
	        destination = destStream.getChannel();

	        long count = 0;
	        long size = source.size();              
	        while((count += destination.transferFrom(source, count, size-count))<size);
	    }
	    finally {
	    	if (sourceStream != null) {
	    		sourceStream.close();
	    	}
	    	
	    	if (destStream != null) {
	    		destStream.close();
	    	}
	        
	    	if(source != null) {
	            source.close();
	        }
	        
	        if(destination != null) {
	            destination.close();
	        }
	    }
	}
	
	private File setLocalDirectory() throws FTPManagerException {
		if (localDir == null) {
			LOGGER.error("The local directory path must be set");
			throw new FTPManagerException ("The local directory path must be set");
		}

		File folder = new File(localDir);
		if (!(folder.isDirectory() && folder.canRead())) { 
			LOGGER.error("The local directory path "+ folder.getAbsolutePath() + 
					" is not a directory or can not be read");
			throw new FTPManagerException ("The local directory path "+ folder.getAbsolutePath() + 
					" is not a directory or can not be read");
		}
		return folder;
	}

	
	private void setRemoteDirectory() throws IOException, FTPManagerException {
		int reply;
		if (remoteDir != null ) {
			ftp.changeWorkingDirectory(remoteDir);
			reply = ftp.getReplyCode();

			if(!FTPReply.isPositiveCompletion(reply)) {
				LOGGER.error("Unable to change working directory " +
						"to: " + remoteDir + ": " + reply);
				throw new FTPManagerException ("Unable to change working directory " +
						"to: " + remoteDir + ": " + reply);
			}
		}
	}

	
	private void connectionMode() {
		// Set mode - passive or active

		if (connectionMode.equalsIgnoreCase("passive")) {
			ftp.enterLocalPassiveMode();
		} else if (connectionMode.equalsIgnoreCase("active")) {
			ftp.enterLocalActiveMode();
		} else {
			ftp.enterLocalPassiveMode();
		}
	}

	
	private void transferMode() throws IOException {
		// Set transfer type
		if (transferMode.equalsIgnoreCase("ascii")) {
			ftp.setFileType(FTP.ASCII_FILE_TYPE);
		} else if (transferMode.equalsIgnoreCase("binary")) {
			ftp.setFileType(FTP.BINARY_FILE_TYPE);
		}
		else {
			ftp.setFileType(FTP.ASCII_FILE_TYPE);
		}
	}

	
	private void login() throws IOException, FTPManagerException {
		if (!ftp.login(username, password)) {
			LOGGER.error("Unable to login to FTP server " +
		            "using username " + username +
		            ". Check username and password.");
			throw new FTPManagerException ("Unable to login to FTP server " +
					"using username " + username +
					". Check username and password.");
		}
	}

	
	private void connect() throws FTPManagerException {
		ftp.setConnectTimeout(connectTimeout);
		
		try {
			ftp.connect(hostName, port);
		} catch (SocketException e) {
			LOGGER.error("FTP server "+ hostName + " refused connection at port " + port,e);
			throw new FTPManagerException ("FTP server refused connection.", e);
		} catch (IOException e) {
			LOGGER.error("FTP server "+ hostName + " refused connection at port " + port,e);
			throw new FTPManagerException ("FTP server refused connection.", e);
		}
		int reply = ftp.getReplyCode();
		if(!FTPReply.isPositiveCompletion(reply)) {
			LOGGER.error("FTP server "+ hostName + " refused connection with status " + ftp.getReplyString());
			throw new FTPManagerException ("FTP server " + hostName + " refused connection with status " + ftp.getReplyString());
		}
	}

	
}
