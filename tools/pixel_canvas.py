"""Small integer-grid drawing primitives for Niobium sprite source art."""

TRANSPARENT = "."

class Canvas:
    def __init__(self, size=48):
        self.size = size
        self.grid = [[TRANSPARENT] * size for _ in range(size)]
    def pixel(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.grid[y][x] = color
    def rect(self, x, y, w, h, color):
        for yy in range(y, y + h):
            for xx in range(x, x + w): self.pixel(xx, yy, color)
    def line(self, x0, y0, x1, y1, color):
        dx, dy = abs(x1-x0), -abs(y1-y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        error = dx + dy
        while True:
            self.pixel(x0, y0, color)
            if x0 == x1 and y0 == y1: break
            twice = 2 * error
            if twice >= dy: error += dy; x0 += sx
            if twice <= dx: error += dx; y0 += sy
    def polygon(self, points, color):
        for y in range(max(0, min(p[1] for p in points)), min(self.size, max(p[1] for p in points)+1)):
            crossings = []
            for a, b in zip(points, points[1:] + points[:1]):
                if (a[1] <= y < b[1]) or (b[1] <= y < a[1]):
                    crossings.append(a[0] + (y-a[1]) * (b[0]-a[0]) / (b[1]-a[1]))
            crossings.sort()
            for left, right in zip(crossings[::2], crossings[1::2]):
                for x in range(max(0, int(left+0.5)), min(self.size, int(right+0.5)+1)):
                    self.pixel(x, y, color)
        for a, b in zip(points, points[1:] + points[:1]): self.line(*a, *b, color)
    def ellipse(self, cx, cy, rx, ry, color):
        for y in range(cy-ry, cy+ry+1):
            for x in range(cx-rx, cx+rx+1):
                if ((x-cx)/max(rx,1))**2 + ((y-cy)/max(ry,1))**2 <= 1.02:
                    self.pixel(x,y,color)
    def outline(self):
        boundary=[]
        for y in range(self.size):
            for x in range(self.size):
                if self.grid[y][x] != TRANSPARENT: continue
                if any(0<=x+dx<self.size and 0<=y+dy<self.size and self.grid[y+dy][x+dx] not in (TRANSPARENT,"K")
                       for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): boundary.append((x,y))
        for x,y in boundary: self.pixel(x,y,"K")
        return self
    def rows(self): return ["".join(row) for row in self.grid]
    p=pixel; r=rect; e=ellipse; poly=polygon
