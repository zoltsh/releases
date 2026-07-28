package sh.zolt.releases.cli;

@FunctionalInterface
public interface ReleaseCommand {
    void execute(String[] arguments);
}
