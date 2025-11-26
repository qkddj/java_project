package com.swingauth.util;

import com.swingauth.db.Mongo;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;

/**
 * 평점 관련 유틸리티 클래스
 */
public class RatingUtil {
    
    /**
     * 특정 사용자의 평균 평점을 조회합니다.
     * @param username 사용자명
     * @return 평균 평점 (없으면 5.0)
     */
    public static double getUserAverageRating(String username) {
        try {
            var userDoc = Mongo.users().find(Filters.eq("username", username)).first();
            if (userDoc == null) return 5.0;
            
            Object id = userDoc.get("_id");
            if (!(id instanceof ObjectId)) return 5.0;
            
            ObjectId userId = (ObjectId) id;
            
            var avgDoc = Mongo.ratings().find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("serviceType", "average")
                )
            ).first();
            
            if (avgDoc == null) return 5.0;
            
            Object ar = avgDoc.get("averageRating");
            if (ar instanceof Double) {
                return (Double) ar;
            } else if (ar instanceof Number) {
                return ((Number) ar).doubleValue();
            }
            return 5.0;
        } catch (Exception e) {
            return 5.0;
        }
    }
    
    /**
     * 특정 사용자의 평균 평점을 초기화합니다 (삭제).
     * @param username 사용자명
     * @return 삭제된 문서 수
     */
    public static int resetUserAverageRating(String username) {
        try {
            var userDoc = Mongo.users().find(Filters.eq("username", username)).first();
            if (userDoc == null) return 0;
            
            Object id = userDoc.get("_id");
            if (!(id instanceof ObjectId)) return 0;
            
            ObjectId userId = (ObjectId) id;
            
            var result = Mongo.ratings().deleteMany(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("serviceType", "average")
                )
            );
            
            return (int) result.getDeletedCount();
            
        } catch (Exception e) {
            System.err.println("[오류] 평균 평점 초기화 실패: " + username);
            return 0;
        }
    }
    
    /**
     * 특정 사용자 ID의 평균 평점을 초기화합니다 (삭제).
     * @param userId 사용자 ObjectId
     * @return 삭제된 문서 수
     */
    public static int resetUserAverageRating(ObjectId userId) {
        try {
            var result = Mongo.ratings().deleteMany(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("serviceType", "average")
                )
            );
            return (int) result.getDeletedCount();
        } catch (Exception e) {
            System.err.println("[오류] 평균 평점 초기화 실패: " + userId);
            return 0;
        }
    }
    
    /**
     * 모든 사용자의 평균 평점을 초기화합니다 (삭제).
     * 주의: 모든 평균 평점이 삭제됩니다!
     * @return 삭제된 문서 수
     */
    public static int resetAllAverageRatings() {
        try {
            var result = Mongo.ratings().deleteMany(
                Filters.eq("serviceType", "average")
            );
            return (int) result.getDeletedCount();
        } catch (Exception e) {
            System.err.println("[오류] 전체 평균 평점 초기화 실패");
            return 0;
        }
    }
    
    /**
     * 특정 문서 ID로 평균 평점 문서를 삭제합니다.
     * @param documentId 문서 ObjectId
     * @return 삭제 성공 여부
     */
    public static boolean deleteAverageRatingDocument(ObjectId documentId) {
        try {
            var result = Mongo.ratings().deleteOne(Filters.eq("_id", documentId));
            return result.getDeletedCount() > 0;
        } catch (Exception e) {
            System.err.println("[오류] 평점 문서 삭제 실패: " + documentId);
            return false;
        }
    }
    
    /**
     * 특정 문서 ID 문자열로 평균 평점 문서를 삭제합니다.
     * @param documentIdStr 문서 ObjectId 문자열
     * @return 삭제 성공 여부
     */
    public static boolean deleteAverageRatingDocument(String documentIdStr) {
        try {
            ObjectId documentId = new ObjectId(documentIdStr);
            return deleteAverageRatingDocument(documentId);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 특정 사용자의 평균 평점을 재계산합니다.
     * @param username 사용자명
     * @return 재계산 성공 여부
     */
    public static boolean recalculateUserAverageRating(String username) {
        try {
            var userDoc = Mongo.users().find(Filters.eq("username", username)).first();
            if (userDoc == null) return false;
            
            Object id = userDoc.get("_id");
            if (!(id instanceof ObjectId)) return false;
            
            ObjectId userId = (ObjectId) id;
            
            // 기존 평균 평점 삭제
            resetUserAverageRating(userId);
            
            // 평점 재계산
            double sum = 0.0;
            int count = 0;
            
            for (var doc : Mongo.ratings().find(
                Filters.and(
                    Filters.or(
                        Filters.eq("user1Id", userId),
                        Filters.eq("user2Id", userId)
                    ),
                    Filters.ne("serviceType", "average")
                )
            )) {
                ObjectId docUser1Id = doc.get("user1Id", ObjectId.class);
                ObjectId docUser2Id = doc.get("user2Id", ObjectId.class);
                
                if (docUser1Id != null && docUser1Id.equals(userId)) {
                    Object user1Rating = doc.get("user1Rating");
                    if (user1Rating != null && user1Rating instanceof Number) {
                        sum += ((Number) user1Rating).doubleValue();
                        count++;
                    }
                } else if (docUser2Id != null && docUser2Id.equals(userId)) {
                    Object user2Rating = doc.get("user2Rating");
                    if (user2Rating != null && user2Rating instanceof Number) {
                        sum += ((Number) user2Rating).doubleValue();
                        count++;
                    }
                }
            }
            
            double averageRating = count > 0 ? sum / count : 5.0;
            
            var filter = new org.bson.Document("userId", userId)
                .append("serviceType", "average");
            
            var upsertDoc = new org.bson.Document("userId", userId)
                .append("serviceType", "average")
                .append("averageRating", averageRating);
            
            Mongo.ratings().updateOne(filter, new org.bson.Document("$set", upsertDoc)
                .append("$unset", new org.bson.Document("user1Id", "").append("user2Id", "")), 
                new com.mongodb.client.model.UpdateOptions().upsert(true));
            
            return true;
            
        } catch (Exception e) {
            System.err.println("[오류] 평균 평점 재계산 실패: " + username);
            return false;
        }
    }
    
    /**
     * 두 유저의 평균 평점을 모두 조회합니다.
     * @param userId1 첫 번째 유저 ObjectId (문자열 또는 ObjectId)
     * @param userId2 두 번째 유저 ObjectId (문자열 또는 ObjectId)
     * @return 두 유저의 평균 평점 정보를 담은 문자열
     */
    public static String getTwoUsersAverageRatings(Object userId1, Object userId2) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("[두 유저 평균 평점 조회]\n");
        result.append("입력값 - userId1: ").append(userId1).append(", userId2: ").append(userId2).append("\n");
        result.append("----------------------------------------\n");
        
        try {
            String user1Username = null;
            String user2Username = null;
            
            // userId1 처리
            ObjectId user1Id = null;
            if (userId1 instanceof ObjectId) {
                user1Id = (ObjectId) userId1;
            } else if (userId1 instanceof String) {
                String input1 = (String) userId1;
                try {
                    user1Id = new ObjectId(input1);
                } catch (Exception e) {
                    // 문자열이 ObjectId 형식이 아니면 username으로 조회
                    var userDoc = Mongo.users().find(Filters.eq("username", input1)).first();
                    if (userDoc != null) {
                        user1Username = input1;
                        Object id = userDoc.get("_id");
                        if (id instanceof ObjectId) {
                            user1Id = (ObjectId) id;
                        }
                    }
                }
            }
            
            // userId1의 username 조회 (ObjectId인 경우)
            if (user1Id != null && user1Username == null) {
                var userDoc = Mongo.users().find(Filters.eq("_id", user1Id)).first();
                if (userDoc != null) {
                    user1Username = userDoc.getString("username");
                }
            }
            
            // userId2 처리
            ObjectId user2Id = null;
            if (userId2 instanceof ObjectId) {
                user2Id = (ObjectId) userId2;
            } else if (userId2 instanceof String) {
                String input2 = (String) userId2;
                try {
                    user2Id = new ObjectId(input2);
                } catch (Exception e) {
                    // 문자열이 ObjectId 형식이 아니면 username으로 조회
                    var userDoc = Mongo.users().find(Filters.eq("username", input2)).first();
                    if (userDoc != null) {
                        user2Username = input2;
                        Object id = userDoc.get("_id");
                        if (id instanceof ObjectId) {
                            user2Id = (ObjectId) id;
                        }
                    }
                }
            }
            
            // userId2의 username 조회 (ObjectId인 경우)
            if (user2Id != null && user2Username == null) {
                var userDoc = Mongo.users().find(Filters.eq("_id", user2Id)).first();
                if (userDoc != null) {
                    user2Username = userDoc.getString("username");
                }
            }
            
            // ========== userId1 정보 ==========
            result.append("\n[userId1 정보]\n");
            if (user1Id == null) {
                result.append("❌ userId1을 찾을 수 없습니다: ").append(userId1).append("\n");
            } else {
                result.append("✅ userId1: ").append(user1Id).append("\n");
                if (user1Username != null) {
                    result.append("✅ username: ").append(user1Username).append("\n");
                }
                
                var avgDoc1 = Mongo.ratings().find(
                    Filters.and(
                        Filters.eq("userId", user1Id),
                        Filters.eq("serviceType", "average")
                    )
                ).first();
                
                if (avgDoc1 != null) {
                    Object ar1 = avgDoc1.get("averageRating");
                    double avg1 = 5.0;
                    if (ar1 instanceof Double) {
                        avg1 = (Double) ar1;
                    } else if (ar1 instanceof Number) {
                        avg1 = ((Number) ar1).doubleValue();
                    }
                    result.append("✅ 평균 평점: ").append(avg1).append("점\n");
                } else {
                    result.append("⚠️  평균 평점: 없음 (기본값 5.0점)\n");
                    result.append("  - 평균 평점 문서가 아직 생성되지 않았습니다.\n");
                }
            }
            
            // ========== userId2 정보 ==========
            result.append("\n[userId2 정보]\n");
            if (user2Id == null) {
                result.append("❌ userId2를 찾을 수 없습니다: ").append(userId2).append("\n");
            } else {
                result.append("✅ userId2: ").append(user2Id).append("\n");
                if (user2Username != null) {
                    result.append("✅ username: ").append(user2Username).append("\n");
                }
                
                var avgDoc2 = Mongo.ratings().find(
                    Filters.and(
                        Filters.eq("userId", user2Id),
                        Filters.eq("serviceType", "average")
                    )
                ).first();
                
                if (avgDoc2 != null) {
                    Object ar2 = avgDoc2.get("averageRating");
                    double avg2 = 5.0;
                    if (ar2 instanceof Double) {
                        avg2 = (Double) ar2;
                    } else if (ar2 instanceof Number) {
                        avg2 = ((Number) ar2).doubleValue();
                    }
                    result.append("✅ 평균 평점: ").append(avg2).append("점\n");
                } else {
                    result.append("⚠️  평균 평점: 없음 (기본값 5.0점)\n");
                    result.append("  - 평균 평점 문서가 아직 생성되지 않았습니다.\n");
                }
            }
            
            // ========== 두 유저 쌍의 평점 정보 (serviceType: "video" 문서) ==========
            if (user1Id != null && user2Id != null) {
                result.append("\n[두 유저 쌍 평점 정보]\n");
                
                // user1Id, user2Id로 정렬하여 일관된 검색
                org.bson.types.ObjectId sortedUser1Id, sortedUser2Id;
                if (user1Id.compareTo(user2Id) < 0) {
                    sortedUser1Id = user1Id;
                    sortedUser2Id = user2Id;
                } else {
                    sortedUser1Id = user2Id;
                    sortedUser2Id = user1Id;
                }
                
                var pairDoc = Mongo.ratings().find(
                    Filters.and(
                        Filters.eq("user1Id", sortedUser1Id),
                        Filters.eq("user2Id", sortedUser2Id),
                        Filters.eq("serviceType", "video")
                    )
                ).first();
                
                if (pairDoc != null) {
                    Object user1RatingObj = pairDoc.get("user1Rating");
                    Object user2RatingObj = pairDoc.get("user2Rating");
                    Object user1AvgObj = pairDoc.get("user1AverageRating");
                    Object user2AvgObj = pairDoc.get("user2AverageRating");
                    Object averageObj = pairDoc.get("average");
                    
                    double user1Rating = 0.0;
                    double user2Rating = 0.0;
                    double user1Avg = 5.0;
                    double user2Avg = 5.0;
                    double pairAverage = 5.0;
                    
                    if (user1RatingObj != null && user1RatingObj instanceof Number) {
                        user1Rating = ((Number) user1RatingObj).doubleValue();
                    }
                    if (user2RatingObj != null && user2RatingObj instanceof Number) {
                        user2Rating = ((Number) user2RatingObj).doubleValue();
                    }
                    if (user1AvgObj != null && user1AvgObj instanceof Number) {
                        user1Avg = ((Number) user1AvgObj).doubleValue();
                    }
                    if (user2AvgObj != null && user2AvgObj instanceof Number) {
                        user2Avg = ((Number) user2AvgObj).doubleValue();
                    }
                    if (averageObj != null && averageObj instanceof Number) {
                        pairAverage = ((Number) averageObj).doubleValue();
                    }
                    
                    // user1 정보 표시 (user1 (평균평점) 형식)
                    result.append("  - user1");
                    if (sortedUser1Id.equals(user1Id)) {
                        result.append(" (").append(user1Avg).append(")");
                        if (user1Username != null) {
                            result.append(" - username: ").append(user1Username);
                        }
                        result.append(" - userId: ").append(user1Id);
                        if (user1Rating > 0) {
                            result.append(" - 상대방에게 준 평점: ").append(user1Rating).append("점");
                        }
                    } else {
                        result.append(" (").append(user2Avg).append(")");
                        if (user2Username != null) {
                            result.append(" - username: ").append(user2Username);
                        }
                        result.append(" - userId: ").append(user2Id);
                        if (user2Rating > 0) {
                            result.append(" - 상대방에게 준 평점: ").append(user2Rating).append("점");
                        }
                    }
                    result.append("\n");
                    
                    // user2 정보 표시 (user2 (평균평점) 형식)
                    result.append("  - user2");
                    if (sortedUser2Id.equals(user2Id)) {
                        result.append(" (").append(user2Avg).append(")");
                        if (user2Username != null) {
                            result.append(" - username: ").append(user2Username);
                        }
                        result.append(" - userId: ").append(user2Id);
                        if (user2Rating > 0) {
                            result.append(" - 상대방에게 준 평점: ").append(user2Rating).append("점");
                        }
                    } else {
                        result.append(" (").append(user1Avg).append(")");
                        if (user1Username != null) {
                            result.append(" - username: ").append(user1Username);
                        }
                        result.append(" - userId: ").append(user1Id);
                        if (user1Rating > 0) {
                            result.append(" - 상대방에게 준 평점: ").append(user1Rating).append("점");
                        }
                    }
                    result.append("\n");
                    
                    result.append("  - 유저 쌍 평균: ").append(pairAverage).append("점\n");
                } else {
                    result.append("  ⚠️ 두 유저 쌍의 평점 문서가 없습니다.\n");
                }
                
                // ========== 합산 평균 ==========
                result.append("\n[합산 평균]\n");
                var avgDoc1 = Mongo.ratings().find(
                    Filters.and(
                        Filters.eq("userId", user1Id),
                        Filters.eq("serviceType", "average")
                    )
                ).first();
                var avgDoc2 = Mongo.ratings().find(
                    Filters.and(
                        Filters.eq("userId", user2Id),
                        Filters.eq("serviceType", "average")
                    )
                ).first();
                
                double avg1 = 5.0;
                double avg2 = 5.0;
                
                if (avgDoc1 != null) {
                    Object ar1 = avgDoc1.get("averageRating");
                    if (ar1 instanceof Double) {
                        avg1 = (Double) ar1;
                    } else if (ar1 instanceof Number) {
                        avg1 = ((Number) ar1).doubleValue();
                    }
                }
                
                if (avgDoc2 != null) {
                    Object ar2 = avgDoc2.get("averageRating");
                    if (ar2 instanceof Double) {
                        avg2 = (Double) ar2;
                    } else if (ar2 instanceof Number) {
                        avg2 = ((Number) ar2).doubleValue();
                    }
                }
                
                double combinedAvg = (avg1 + avg2) / 2.0;
                result.append("  - userId1 평균: ").append(avg1).append("점");
                if (user1Username != null) {
                    result.append(" (username: ").append(user1Username).append(")");
                }
                result.append("\n");
                result.append("  - userId2 평균: ").append(avg2).append("점");
                if (user2Username != null) {
                    result.append(" (username: ").append(user2Username).append(")");
                }
                result.append("\n");
                result.append("  - 합산 평균: ").append(combinedAvg).append("점\n");
                result.append("  - 매칭 가능 여부: ").append(combinedAvg >= 4.0 ? "✅ 가능 (4점 이상)" : "❌ 불가능 (4점 미만)").append("\n");
            }
            
        } catch (Exception e) {
            result.append("\n❌ 오류 발생: ").append(e.getMessage()).append("\n");
        }
        
        result.append("\n========================================\n");
        
        return result.toString();
    }
    
    /**
     * 두 유저의 평균 평점을 모두 조회합니다 (username 사용).
     * @param username1 첫 번째 유저명
     * @param username2 두 번째 유저명
     * @return 두 유저의 평균 평점 정보를 담은 문자열
     */
    public static String getTwoUsersAverageRatingsByUsername(String username1, String username2) {
        return getTwoUsersAverageRatings(username1, username2);
    }
}

