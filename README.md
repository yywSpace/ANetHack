# 参考
https://github.com/gurrhack/NetHack-Android
# 重构建
## 在Linux上构建原始程序，获取资源文件及tile.c
```shell
git colne https://github.com/NetHack/NetHack.git
cd NetHack/sys/unix
./setup.sh hints/linux.370
cd ../../
make 
make install
# 复制~/nh/install/games/lib/nethackdir文件夹到Android工程的assets目录下
# 复制sysconf到Android工程的assets/nethackdir目录下
cd NetHack/util
make ../src/tile.c
# 复制NetHack/src/tile.c到Android工程的cpp目录下
```
## 在Window上用AndroidStudio编译并执行程序
1. 进入Android工程/cpp/Lua 拉取子模块
2. 进入Android工程/cpp/NetHack 拉取子模块
3. 编译执行