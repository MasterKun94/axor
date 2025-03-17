package io.masterkun.axor.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A utility class for parsing string representations of message types into {@code MsgType} objects.
 * The parser supports nested and parameterized types, and it constructs a tree of nodes to represent the structure of the type.
 * This class is primarily used by the {@link MsgType#parse(String)} method to convert string representations into their corresponding {@code MsgType} instances.
 */
class MsgTypeParser {

    public static MsgType<?> parse(String s) {
        Node root = new Node("");
        int parsed = parse(s, 0, root);
        assert parsed == s.length() : parsed + " != " + s.length();
        assert root.children.size() == 1;
        try {
            return root.children.getFirst().toMsgType();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static int parse(String s, int start, Node parent) {
        for (int i = start, l = s.length(); i < l; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> {
                    Node child = new Node(s.substring(start, i));
                    parent.addChild(child);
                    i = parse(s, i + 1, child);
                    start = i + 1;
                }
                case '>' -> {
                    if (start < i) {
                        Node child = new Node(s.substring(start, i));
                        parent.addChild(child);
                    }
                    return i + 1;
                }
                case ',' -> {
                    Node child = new Node(s.substring(start, i));
                    parent.addChild(child);
                    start = i + 1;
                }
                case ' ' -> {
                    if (start == i) {
                        start = i + 1;
                    } else {
                        throw new RuntimeException("Unexpected blank space at index " + i);
                    }
                }
                default -> {
                }
            }
        }
        if (start < s.length()) {
            parent.addChild(new Node(s.substring(start)));
        }
        return s.length();
    }

    private static class Node {
        private final String content;
        private final List<Node> children;

        private Node(String content) {
            this.content = content;
            this.children = new ArrayList<>();
        }

        public void addChild(Node child) {
            children.add(child);
        }

        public MsgType<?> toMsgType() throws ClassNotFoundException {
            Class<?> type = Class.forName(content);
            if (children.isEmpty()) {
                return MsgType.of(type);
            }
            List<MsgType<?>> msgTypes = new ArrayList<>(children.size());
            for (Node child : children) {
                msgTypes.add(child.toMsgType());
            }
            return Unsafe.msgType(type, Collections.unmodifiableList(msgTypes));
        }
    }
}
