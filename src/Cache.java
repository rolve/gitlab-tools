import java.util.*;

import org.gitlab4j.api.GitLabApiException;

public class Cache<V> {

    private final Map<Key, V> entries = new HashMap<>();

    public V update(Key key, ValSupplier<V> v) throws GitLabApiException {
        V value = entries.get(key);
        if (value == null) {
            value = v.get();
            entries.put(key, value);
        }
        return value;
    }

    public interface ValSupplier<V> {
        V get() throws GitLabApiException;
    }

    public static class Key {
        private final String url;
        private final String token;
        private final Object aux;

        public Key(Cmd<?> cmd, Object aux) {
            this.url = cmd.args.getGitlabUrl();
            this.token = cmd.token;
            this.aux = aux;
        }

        @Override
        public int hashCode() {
            int result = 31 + url.hashCode();
            result = 31 * result + token.hashCode();
            result = 31 * result + ((aux == null) ? 0 : aux.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            var other = (Key) obj;
            return url.equals(other.url) && token.equals(other.token)
                    && Objects.equals(aux, other.aux);
        }
    }
}
