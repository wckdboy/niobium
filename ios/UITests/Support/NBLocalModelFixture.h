#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
/// Loopback-only OpenAI-compatible SSE fixture in the test runner, never the app.
@interface NBLocalModelFixture : NSObject
- (nullable instancetype)init;
@property(nonatomic, readonly) NSString *baseURL;
@property(nonatomic, readonly) NSString *marker;
@property(atomic, readonly) NSUInteger requestCount;
@property(nonatomic) NSTimeInterval replyDelay;
- (void)stop;
@end
NS_ASSUME_NONNULL_END
