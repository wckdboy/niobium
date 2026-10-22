#import "NBLocalModelFixture.h"
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>

@interface NBLocalModelFixture ()
@property(nonatomic) int fd;
@property(nonatomic) uint16_t port;
@property(nonatomic) dispatch_source_t source;
@property(nonatomic) dispatch_queue_t queue;
@property(atomic, readwrite) NSUInteger requestCount;
@end

@implementation NBLocalModelFixture
- (instancetype)init {
    if (!(self = [super init])) return nil;
    _marker = NSUUID.UUID.UUIDString;
    _fd = socket(AF_INET, SOCK_STREAM, 0);
    if (_fd < 0) return nil;
    struct sockaddr_in address = {.sin_len = sizeof(address), .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK)};
    if (bind(_fd, (struct sockaddr *)&address, sizeof(address)) || listen(_fd, 8)) {
        close(_fd); _fd = -1; return nil;
    }
    socklen_t size = sizeof(address);
    getsockname(_fd, (struct sockaddr *)&address, &size);
    _port = ntohs(address.sin_port);
    _queue = dispatch_queue_create("niobium.model-fixture", DISPATCH_QUEUE_CONCURRENT);
    _source = dispatch_source_create(DISPATCH_SOURCE_TYPE_READ, _fd, 0, _queue);
    __weak typeof(self) weakSelf = self;
    dispatch_source_set_event_handler(_source, ^{ [weakSelf acceptRequest]; });
    dispatch_resume(_source);
    return self;
}
- (NSString *)baseURL { return [NSString stringWithFormat:@"http://127.0.0.1:%u", self.port]; }
- (void)stop {
    if (self.source) { dispatch_source_cancel(self.source); self.source = nil; }
    if (self.fd >= 0) { close(self.fd); self.fd = -1; }
}
- (void)dealloc { [self stop]; }
- (void)acceptRequest {
    int peer = accept(self.fd, NULL, NULL);
    if (peer < 0) return;
    dispatch_async(self.queue, ^{
        struct timeval timeout = {.tv_sec = 5};
        setsockopt(peer, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(peer, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
        int one = 1; setsockopt(peer, SOL_SOCKET, SO_NOSIGPIPE, &one, sizeof(one));
        [self serve:peer]; close(peer);
    });
}
- (void)write:(NSString *)text to:(int)peer {
    NSData *data = [text dataUsingEncoding:NSUTF8StringEncoding];
    const uint8_t *bytes = data.bytes;
    NSUInteger sent = 0;
    while (sent < data.length) {
        ssize_t count = send(peer, bytes + sent, data.length - sent, 0);
        if (count <= 0) return;
        sent += count;
    }
}
- (NSString *)event:(NSString *)text finish:(BOOL)finish {
    NSDictionary *chunk = @{@"id": @"niobium-local", @"object": @"chat.completion.chunk",
        @"created": @1700000000, @"model": @"deepseek-chat", @"choices": @[
            @{@"index": @0, @"delta": @{@"content": text},
              @"finish_reason": finish ? @"stop" : NSNull.null}]};
    NSData *data = [NSJSONSerialization dataWithJSONObject:chunk options:0 error:nil];
    return [NSString stringWithFormat:@"data: %@\n\n", [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]];
}
- (void)serve:(int)peer {
    NSMutableData *buffer = [NSMutableData data];
    NSData *separator = [@"\r\n\r\n" dataUsingEncoding:NSUTF8StringEncoding];
    NSRange split = NSMakeRange(NSNotFound, 0);
    uint8_t bytes[8192];
    while (buffer.length <= 32768 && split.location == NSNotFound) {
        ssize_t count = recv(peer, bytes, sizeof(bytes), 0);
        if (count <= 0) return;
        [buffer appendBytes:bytes length:count];
        split = [buffer rangeOfData:separator options:0 range:NSMakeRange(0, buffer.length)];
    }
    if (split.location == NSNotFound || split.location > 32768) return;
    NSString *head = [[NSString alloc] initWithData:[buffer subdataWithRange:NSMakeRange(0, split.location)] encoding:NSUTF8StringEncoding];
    NSUInteger length = 0;
    for (NSString *line in [head componentsSeparatedByString:@"\r\n"])
        if ([line.lowercaseString hasPrefix:@"content-length:"]) length = [[line substringFromIndex:15] integerValue];
    if (length > 1024 * 1024) return;
    NSUInteger start = split.location + split.length;
    while (buffer.length - start < length) {
        ssize_t count = recv(peer, bytes, MIN(sizeof(bytes), length - (buffer.length - start)), 0);
        if (count <= 0) return;
        [buffer appendBytes:bytes length:count];
    }
    if ([head hasPrefix:@"GET "] && [head containsString:@"/models "]) {
        [self write:@"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"data\":[{\"id\":\"deepseek-chat\",\"object\":\"model\"}]}" to:peer];
        return;
    }
    if (![head hasPrefix:@"POST "] || ![head containsString:@"/chat/completions "]) return;
    id payload = [NSJSONSerialization JSONObjectWithData:[buffer subdataWithRange:NSMakeRange(start, length)] options:0 error:nil];
    if (![payload isKindOfClass:NSDictionary.class]) return;
    @synchronized(self) { self.requestCount += 1; }
    [self write:@"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n" to:peer];
    [self write:[self event:[NSString stringWithFormat:@"LOCAL DSH %@ ", self.marker] finish:NO] to:peer];
    if (self.replyDelay > 0) [NSThread sleepForTimeInterval:MIN(self.replyDelay, 20)];
    [self write:[self event:@"DEVICE ROUND TRIP" finish:YES] to:peer];
    [self write:@"data: [DONE]\n\n" to:peer];
}
@end
