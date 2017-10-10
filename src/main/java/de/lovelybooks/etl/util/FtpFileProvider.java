package de.lovelybooks.etl.util;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipInputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FtpFileProvider {

    private static Logger log = LoggerFactory.getLogger(FtpFileProvider.class.getName());

    private FTPClient ftp;

    @Value("${vlb.ftp.host}")
    private String host;
    @Value("${vlb.ftp.user}")
    private String user;
    @Value("${vlb.ftp.password}")
    private String password;
    @Value("${vlb.ftp.path}")
    private String pathToOnixZipFile;
    @Value("${vlb.zip.nameformat}")
    private String zipNameFormat;

    public FtpFileProvider() {
        ftp = new FTPClient();
    }

    public boolean connect() {
        boolean connected = false;

        try {
            ftp.connect(host);
            connected = ftp.login(user, password);
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.enterLocalPassiveMode();
        } catch (IOException e) {
            log.error("Error connecting to FTP. Will exit process...");
        }
        log.info("Connected to FTP Server.");
        return connected;
    }

    public ZipInputStream openZipFile() {
        LocalDate date = LocalDate.now();
        String zipFile = zipNameFormat.replaceAll("\\[date\\]", date.format(DateTimeFormatter.BASIC_ISO_DATE));
        try {
            return new ZipInputStream(ftp.retrieveFileStream(pathToOnixZipFile + zipFile));
        } catch (IOException e) {
            log.error("Could not stream file " + zipFile + ".");
        }
        return null;
    }

    public void disconnect(InputStream inputStream) {
        try {
            inputStream.close();
            ftp.completePendingCommand();
            ftp.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
