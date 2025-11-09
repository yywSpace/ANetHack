#ifndef ANETHACK_ANDROIDRECOVER_H
#define ANETHACK_ANDROIDRECOVER_H

// androidrecover.c
// copy and change function name from recover.c
int and_restore_savefile(char *);
void and_set_levelfile_name(int);
int and_open_levelfile(int);
int and_create_savefile(void);

#endif //ANETHACK_ANDROIDRECOVER_H
