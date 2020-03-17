package gitlabtools;

import static java.util.Objects.requireNonNull;

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
        private final Object aux;

        public Key(String url, Object aux) {
            this.url = requireNonNull(url);
            this.aux = aux;
        }

        @Override
        public int hashCode() {
            int result = 31 + url.hashCode();
            result = 31 * result + ((aux == null) ? 0 : aux.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            var other = (Key) obj;
            return url.equals(other.url) && Objects.equals(aux, other.aux);
        }
    }
}
