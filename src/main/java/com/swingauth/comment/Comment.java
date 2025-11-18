package com.swingauth.comment;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;

public class Comment {
  public String id;
  public String postId;          // Post._id (hex string)
  public String authorUsername;
  public String content;
  public Date createdAt;

  public Document toDoc() {
    ObjectId oid = (postId != null) ? new ObjectId(postId) : null;
    return new Document()
        .append("postId", oid)
        .append("authorUsername", authorUsername)
        .append("content", content)
        .append("createdAt", createdAt != null ? createdAt : new Date());
  }

  public static Comment fromDoc(Document d) {
    if (d == null) return null;
    Comment c = new Comment();
    ObjectId cid = d.getObjectId("_id");
    c.id = cid != null ? cid.toHexString() : null;
    ObjectId pid = d.getObjectId("postId");
    c.postId = pid != null ? pid.toHexString() : null;
    c.authorUsername = d.getString("authorUsername");
    c.content = d.getString("content");
    Object ca = d.get("createdAt");
    if (ca instanceof Date) c.createdAt = (Date) ca;
    return c;
  }
}
