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


public class FTPManager {

	static final Logger LOGGER = Logger.getLogger(FTPManager.class);
	private static final String SAVEDIRECTORY = ".save";
	private Properties properties;
	private FTPClient ftp;
	private File localFolder;
	
	public FTPManager(Properties properties) {
		ftp = new FTPClient();
		this.properties = properties;
	}
	
	public void init() throws FTPManagerException{
		try {
			connect();
			login();
            transferMode();
            setMode();
            setRemoteDirectory();            
            localFolder = setLocalDirectory();
            if(LOGGER.isInfoEnabled()) {
        		LOGGER.info(localInfo().toString());
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
	
	
	public void disconnect() {
		try {
            ftp.disconnect();
        } catch (IOException e1) {
            LOGGER.error("Unable to disconnect from FTP server.", e1);
        }
	}
	
	private StringBuffer localInfo() throws IOException {
		StringBuffer strbuf = new StringBuffer();
		strbuf.append("{");
		strbuf.append("server:").append(ftp.getRemoteAddress().getHostAddress()).append(", ");
		strbuf.append("ctrlenc:").append(ftp.getControlEncoding()).append(", ");
		strbuf.append("systemtype:").append(ftp.getSystemType()).append(", ");
		strbuf.append("charset:").append(ftp.getCharset().name()).append(", ");
		strbuf.append("}");
		return strbuf;
		//LOGGER.info("Remote system is " + ftp.getRemoteAddress().getHostAddress());
		//LOGGER.info(ftp.getStatus());
	}

	
	public void putAllDirectory() throws IOException {
		
		int reply;
		File[] listOfFiles = localFolder.listFiles(); 
		int count = 0;
		
		for (count = 0; count < listOfFiles.length; count++) {

			if (listOfFiles[count].isFile()) {
				File file = listOfFiles[count];
				LOGGER.info(file.getAbsoluteFile() + " => ftp => (" + properties.getProperty("todir")+") "+ file.getName());
				boolean sendStatus = false;
				try {
					sendStatus = ftp.storeFile(file.getName(), new FileInputStream(file.getAbsoluteFile()));
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
		            	LOGGER.info("Put error " + ftp.getReplyString());
		            }
				} else {
					if (Boolean.valueOf(properties.getProperty("moveDir","true"))) {
						File moveDir = new File(localFolder,SAVEDIRECTORY);

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
		
		LOGGER.info( (count - 1) + " files sent to " + ftp.getRemoteAddress().getHostAddress());
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
		if (properties.getProperty("fromdir") == null) {
			LOGGER.error("Property fromdir must be set");
			throw new FTPManagerException ("Property fromdir must be set");
		}


		
		File folder = new File(properties.getProperty("fromdir"));
		if (!(folder.isDirectory() && folder.canRead())) { 
			LOGGER.error("Property fromdir "+ folder.getAbsolutePath() + "is not a directory or can not be read");
			throw new FTPManagerException ("Property fromdir "+ folder.getAbsolutePath() + "is not a directory or can not be read");
		}
		return folder;
	}

	
	private void setRemoteDirectory() throws IOException, FTPManagerException {
		int reply;
		if (properties.getProperty("todir") != null ) {
			ftp.changeWorkingDirectory(properties.getProperty("todir"));
			reply = ftp.getReplyCode();

			if(!FTPReply.isPositiveCompletion(reply)) {
				throw new FTPManagerException ("Unable to change working directory " +
						"to: " + properties.getProperty("todir"));
			}
		}
	}

	private void setMode() {
		// Set mode - passive or active
		if (properties.getProperty("mode") == null) {
			ftp.enterLocalPassiveMode();

		} else {
			if (properties.getProperty("mode").equalsIgnoreCase("passive")) {
				ftp.enterLocalPassiveMode();
			} else if (properties.getProperty("mode").equalsIgnoreCase("active")) {
				ftp.enterLocalActiveMode();
			} else {
				ftp.enterLocalPassiveMode();
			}
		}
	}

	private void transferMode() throws IOException {
		// Set transfer type
		if (properties.getProperty("fileType") == null) {
			ftp.setFileType(FTP.ASCII_FILE_TYPE);
		} else {
			if (properties.getProperty("fileType").equalsIgnoreCase("ascii")) {
				ftp.setFileType(FTP.ASCII_FILE_TYPE);
			} else if (properties.getProperty("fileType").equalsIgnoreCase("binary")) {
				ftp.setFileType(FTP.BINARY_FILE_TYPE);
			}
			else {
				ftp.setFileType(FTP.ASCII_FILE_TYPE);
			}
		}
	}

	private void login() throws IOException, FTPManagerException {
		if (!ftp.login(properties.getProperty("username"),properties.getProperty("password"))) {
			LOGGER.error("Unable to login to FTP server " +
		            "using username " + properties.getProperty("username")+" " +
		            "and password " + properties.getProperty("password"));
		    throw new FTPManagerException ("Unable to login to FTP server " +
		                         "using username "+properties.getProperty("username")+" " +
		                         "and password "+properties.getProperty("password"));
		}
	}

	private void connect() throws FTPManagerException {
		ftp.setConnectTimeout(Integer.parseInt(properties.getProperty("timeout", "2000")));
		
		try {
			ftp.connect(properties.getProperty("hostname"));
		} catch (SocketException e) {
			throw new FTPManagerException ("FTP server refused connection.", e);
		} catch (IOException e) {
			throw new FTPManagerException ("FTP server refused connection.", e);
		}
		int reply = ftp.getReplyCode();
		if(!FTPReply.isPositiveCompletion(reply)) {
			throw new FTPManagerException ("FTP server refused connection with status " + ftp.getReplyString());
		}
	}

	
}
