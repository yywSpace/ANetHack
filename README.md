# 参考
https://github.com/gurrhack/NetHack-Android
# 构建
## AndroidStudio编译并执行程序
1. 进入Android工程/cpp/Lua 拉取子模块
2. 进入Android工程/cpp/NetHack 拉取子模块
3. 编译执行

# 更新上游代码及资源
## Merge上游代码并解决冲突
## 更新资源文件
```shell
git colne https://github.com/NetHack/NetHack.git
cd NetHack/sys/unix
./setup.sh hints/linux.370
cd ../../
make all
# 复制NetHack/dat/nhdat到Android工程的assets目录下
cd NetHack/util
make ../src/tile.c
# 复制NetHack/src/tile.c到Android工程的cpp目录下
make tile2bmp
./tile2bmp.o default_tiles.bmp
# 复制NetHack/util/default_tiles.bmp到Android工程的assets/tiles目录下
```
