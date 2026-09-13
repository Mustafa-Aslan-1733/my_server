#include <stdio.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

// configure the server to listen on any address on port socketnum, bind the 
// server socket to the address (assigning a name to the socket) then starts
// listening on the server socket.
int setup_server(int *_serv_socket, struct sockaddr_in6 *server, int socketnum){
    int serv_socket;
    serv_socket = socket(AF_INET6, SOCK_STREAM, 0);
    if (serv_socket == -1){
        puts("could not create socket");
    }

    server->sin6_family = AF_INET6;
    server->sin6_addr = in6addr_any;
    server->sin6_port = htons(socketnum);

    if (bind(serv_socket, (struct sockaddr*) server, sizeof(*server)) < 0){
        return -1;
    }

    puts("bind done");
    listen(serv_socket, 3);
    *_serv_socket = serv_socket;
    return 0;
}


//wait for a connection, then return with the client address info in *client
//and the file descriptor to the accepted socket in _cl_socket.
int accept_connection(int serv_socket, int *_cl_socket, struct sockaddr_in6 *client){
    puts("Waiting for incoming connections");
    int c, cl_socket;
    c = sizeof(struct sockaddr_in6);
    cl_socket = accept(serv_socket, (struct sockaddr*) client, (socklen_t *)&c);
    
    if (cl_socket < 0){
        puts("accept failed");
        puts(strerror(errno));
        return -1;
    }
    puts("connection accepted");
    *_cl_socket = cl_socket;
    return 0;
}

int main(int argc, char** argv){
    int serv_socket, cl_socket, c;
    struct sockaddr_in6 server, client;

    int socketnum = 8000;

    if (argc == 1) {
            if (setup_server(&serv_socket, &server, 80) == -1){
                puts("bind failed");
                puts(strerror(errno));
            }
    } else {
        puts("don't give any arguments (at least for now)");
        return 0;
    }

    int t = 3;
    while (t--) {
        accept_connection(serv_socket, &cl_socket, &client);

        char response[2000], message[2000];
        int msize = 0;
        msize = read(cl_socket, message, 2000);
        message[msize] = '\0';

        char *fpath = "./res/index.html";
        int fd = open(fpath, O_RDONLY);
        if (fd == -1){
            puts("cant open file");
            return 0;
        }
        int rsize = read(fd, response, 2000);
        response[rsize] = '\0';

        write(cl_socket, response, rsize);
        close(cl_socket);
        close(fd);
        close(serv_socket);
    }
    return 0;
} 
