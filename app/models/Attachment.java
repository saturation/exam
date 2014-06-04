package models;

import util.SitnetUtil;

import javax.persistence.*;
import java.io.File;

@Entity
public class Attachment extends SitnetModel {

//	@ManyToOne(cascade = CascadeType.PERSIST)
//	private AbstractQuestion question;

    private String fileName;
    private String filePath;
    private String mimeType;

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public String getFileName() { return this.fileName; }
    public void setFilePath(String filePath) {

        if (this.filePath != null) {
            // We're updating an existing attachment, remove old file
            SitnetUtil.removeAttachmentFile(this.filePath);
        }
        this.filePath = filePath;
    }
    public String getFilePath() {
        return this.filePath;
    }
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    public String getMimeType() {
        return this.mimeType;
    }
}