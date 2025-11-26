package com.swingauth.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.swingauth.db.Mongo;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;

public class RatingService {
    private final MongoCollection<Document> ratings = Mongo.ratings();
    private final MongoCollection<Document> users = Mongo.users();

    /**
     * username으로 사용자의 ObjectId를 찾기
     */
    private ObjectId getUserIdByUsername(String username) {
        if (username == null || username.isBlank() || username.equals("anonymous")) {
            return null;
        }
        Document userDoc = users.find(Filters.eq("username", username)).first();
        if (userDoc != null) {
            return userDoc.getObjectId("_id");
        }
        return null;
    }

    /**
     * ObjectId를 사전순으로 정렬하여 항상 같은 순서로 저장
     */
    private ObjectId[] sortObjectIds(ObjectId id1, ObjectId id2) {
        if (id1 == null && id2 == null) {
            return new ObjectId[]{null, null};
        }
        if (id1 == null) {
            return new ObjectId[]{id2, null};
        }
        if (id2 == null) {
            return new ObjectId[]{id1, null};
        }
        // ObjectId를 문자열로 변환하여 비교
        String str1 = id1.toHexString();
        String str2 = id2.toHexString();
        if (str1.compareTo(str2) <= 0) {
            return new ObjectId[]{id1, id2};
        } else {
            return new ObjectId[]{id2, id1};
        }
    }

    /**
     * 랜덤 채팅 평점 저장
     * @param raterUsername 평점을 준 사용자
     * @param ratedUsername 평점을 받은 사용자 (상대방) - username이어야 함
     * @param rating 평점 (1-5)
     * @return 저장 성공 여부
     */
    public boolean createRating(String raterUsername, String ratedUsername, int rating) {
        if (raterUsername == null || raterUsername.isBlank()) {
            throw new IllegalArgumentException("평점을 주는 사용자 정보가 없습니다.");
        }
        if (ratedUsername == null || ratedUsername.isBlank() || ratedUsername.equals("anonymous")) {
            throw new IllegalArgumentException("상대방 사용자 정보가 필요합니다. (현재 값: " + ratedUsername + ")");
        }
        // rating이 0이면 건너뛰기를 의미 (0점으로 저장)
        // rating이 1-5 사이면 정상 평점
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("평점은 0(건너뛰기) 또는 1-5 사이의 값이어야 합니다.");
        }

        System.out.println("평점 저장 시도: raterUsername=" + raterUsername + ", ratedUsername=" + ratedUsername + ", rating=" + rating);

        // username으로 ObjectId 찾기
        ObjectId raterId = getUserIdByUsername(raterUsername);
        ObjectId ratedId = getUserIdByUsername(ratedUsername);

        if (raterId == null) {
            System.err.println("평점을 주는 사용자를 찾을 수 없습니다: " + raterUsername);
            throw new IllegalArgumentException("평점을 주는 사용자를 찾을 수 없습니다: " + raterUsername);
        }
        if (ratedId == null) {
            System.err.println("평점을 받는 사용자를 찾을 수 없습니다: " + ratedUsername + " (이 값이 실제 username인지 확인하세요)");
            throw new IllegalArgumentException("평점을 받는 사용자를 찾을 수 없습니다: " + ratedUsername);
        }
        
        System.out.println("ObjectId 찾기 성공: raterId=" + raterId + ", ratedId=" + ratedId);

        // ObjectId를 사전순으로 정렬하여 항상 같은 순서로 저장
        ObjectId[] sorted = sortObjectIds(raterId, ratedId);
        ObjectId user1Id = sorted[0];
        ObjectId user2Id = sorted[1];
        
        // 현재 평점을 주는 사용자가 user1Id인지 확인
        boolean isRaterUser1 = raterId.equals(user1Id);

        // 정렬된 user1Id, user2Id로 문서 찾기 (항상 같은 순서로 저장되므로 간단하게 찾을 수 있음)
        Document filter = new Document("user1Id", user1Id)
            .append("user2Id", user2Id)
            .append("serviceType", "randomChat");
        
        // 업데이트 문서 생성
        Document update = new Document();
        if (isRaterUser1) {
            // 현재 평점을 주는 사용자가 user1Id
            update.append("$set", new Document("user1Rating", rating)
                .append("updatedAt", new Date()));
        } else {
            // 현재 평점을 주는 사용자가 user2Id
            update.append("$set", new Document("user2Rating", rating)
                .append("updatedAt", new Date()));
        }
        
        // 평균 계산을 위해 기존 문서를 먼저 조회
        Document existing = ratings.find(filter).first();
        if (existing != null) {
            Integer user1Rating = existing.getInteger("user1Rating");
            Integer user2Rating = existing.getInteger("user2Rating");
            
            // 업데이트 후 평점 값 계산
            int finalUser1Rating = isRaterUser1 ? rating : (user1Rating != null ? user1Rating : 0);
            int finalUser2Rating = isRaterUser1 ? (user2Rating != null ? user2Rating : 0) : rating;
            
            // 두 평점이 모두 있으면 평균 계산
            if (finalUser1Rating > 0 && finalUser2Rating > 0) {
                double averageRating = (finalUser1Rating + finalUser2Rating) / 2.0;
                update.get("$set", Document.class).append("averageRating", Math.round(averageRating * 10.0) / 10.0);
            }
        }
        
        // 새 문서 생성 시 필요한 필드
        update.append("$setOnInsert", new Document()
            .append("createdAt", new Date()));
        
        // upsert를 사용하여 문서가 없으면 생성, 있으면 업데이트
        ratings.updateOne(filter, update, new UpdateOptions().upsert(true));
        
        // 받은 사용자의 평점 통계 업데이트 (0점이 아닌 경우만)
        // 0점은 건너뛰기를 의미하므로 통계에 포함하지 않음
        if (rating > 0) {
            updateUserRatingStats(ratedUsername, rating);
        } else {
            System.out.println("건너뛰기 선택: 평점 통계에 반영하지 않음 (rating=0)");
        }
        
        // 채팅 횟수는 매칭 완료 시 증가하므로 여기서는 처리하지 않음
        
        return true;
    }
    
    /**
     * 사용자가 받은 평점 통계 업데이트
     */
    private void updateUserRatingStats(String username, int rating) {
        if (username == null || username.isBlank()) {
            return;
        }
        
        // 먼저 문서를 조회하여 필드 존재 여부 확인
        Document userDoc = users.find(Filters.eq("username", username)).first();
        if (userDoc == null) {
            System.err.println("사용자를 찾을 수 없습니다: " + username);
            return;
        }
        
        Document setDoc = new Document();
        Document incDoc = new Document();
        
        // 필드가 없으면 기본값으로 설정, 있으면 증가
        if (!userDoc.containsKey("totalRatingReceived")) {
            setDoc.append("totalRatingReceived", rating);
        } else {
            incDoc.append("totalRatingReceived", rating);
        }
        
        if (!userDoc.containsKey("ratingCountReceived")) {
            setDoc.append("ratingCountReceived", 1);
        } else {
            incDoc.append("ratingCountReceived", 1);
        }
        
        // chatCount 필드가 없으면 기본값 설정
        if (!userDoc.containsKey("chatCount")) {
            setDoc.append("chatCount", 0);
        }
        
        // 업데이트 문서 생성
        Document update = new Document();
        if (!setDoc.isEmpty()) {
            update.append("$set", setDoc);
        }
        if (!incDoc.isEmpty()) {
            update.append("$inc", incDoc);
        }
        
        if (!update.isEmpty()) {
            users.updateOne(
                Filters.eq("username", username),
                update
            );
        }
        
        System.out.println("사용자 평점 통계 업데이트: username=" + username + ", 받은 평점=" + rating);
    }
    
    /**
     * 두 사용자 간 평균 평점이 2점 이하인지 확인 (블랙리스트 체크)
     * @param username1 사용자1 username
     * @param username2 사용자2 username
     * @return true면 블랙리스트 (매칭 불가), false면 매칭 가능
     */
    public boolean isBlacklisted(String username1, String username2) {
        if (username1 == null || username1.isBlank() || username2 == null || username2.isBlank()) {
            return false;
        }
        
        ObjectId user1Id = getUserIdByUsername(username1);
        ObjectId user2Id = getUserIdByUsername(username2);
        
        if (user1Id == null || user2Id == null) {
            return false;
        }
        
        // ObjectId 정렬
        ObjectId[] sorted = sortObjectIds(user1Id, user2Id);
        ObjectId sortedUser1Id = sorted[0];
        ObjectId sortedUser2Id = sorted[1];
        
        // 두 사용자 간 평점 문서 찾기
        Document ratingDoc = ratings.find(
            Filters.and(
                Filters.eq("user1Id", sortedUser1Id),
                Filters.eq("user2Id", sortedUser2Id),
                Filters.eq("serviceType", "randomChat")
            )
        ).first();
        
        if (ratingDoc != null) {
            Object avgRatingObj = ratingDoc.get("averageRating");
            if (avgRatingObj instanceof Number) {
                double averageRating = ((Number) avgRatingObj).doubleValue();
                // 평균 평점이 2점 이하이면 블랙리스트
                if (averageRating <= 2.0) {
                    System.out.println("블랙리스트 체크: " + username1 + " <-> " + username2 + 
                                     ", 평균 평점=" + averageRating + " (매칭 불가)");
                    return true;
                }
            }
        }
        
        return false;
    }
}

