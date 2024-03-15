package li.songe.gkd.shizuku;

interface IUserService {
    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String execCommand(String command) = 2;
}