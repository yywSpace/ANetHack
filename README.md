# 参考
https://github.com/gurrhack/NetHack-Android
# 构建
## AndroidStudio编译并执行程序
1. 进入Android工程/cpp/Lua 拉取子模块
2. 进入Android工程/cpp/NetHack 拉取子模块
3. 编译执行

# 更新上游代码及资源
## Merge上游代码并解决冲突
```shell
cd NetHack
# 从Github更新
# git remote add https://github.com/NetHack/NetHack.git 
git remote set-url upstream https://github.com/NetHack/NetHack.git 
# 从镜像库更新
# git remote add upstream https://bgithub.xyz/NetHack/NetHack.git 
git remote set-url upstream https://bgithub.xyz/NetHack/NetHack.git 
# 拉取上游信息并合并
git fetch upstream
git merge upstream/NetHack-3.7
```
## 更新资源文件
```shell
git colne https://github.com/NetHack/NetHack.git
cd NetHack/sys/unix
./setup.sh hints/linux.370
cd ../../
# make fetch-lua
make all
# 复制NetHack/dat/nhdat到Android工程的assets目录下
cd util
make ../src/tile.c
# 复制NetHack/src/tile.c到Android工程的cpp目录下
make tile2bmp
./tile2bmp default_tiles.bmp
# 复制NetHack/util/default_tiles.bmp到Android工程的assets/tiles目录下
```
