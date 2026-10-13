package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Точка входа прототипа:
 * <ul>
 *     <li>{@code Main <spec>} — печать дерева путей (проверка парсинга и группировки);</li>
 *     <li>{@code Main <spec> <outDir> <basePackage> [--root api/v1] [--root-name V1Resource]
 *         [--ignore <regex>]... [--super-interface полный.Класс] [--import Схема=полный.класс]... [--lombok]
 *         [--split-model [--model-package Схема=группа]...]}
 *         — генерация исходников;</li>
 *     <li>{@code Main --help} — справка.</li>
 * </ul>
 * Коды возврата: 0 — успех; 1 — ошибка генерации (спека не читается, пути вне rootPath, коллизии);
 * 2 — ошибка использования (кривые аргументы). Ошибки — в stderr.
 */
public final class Main {

    private static final String DEFAULT_ROOT_PATH = "api/v1";
    private static final String DEFAULT_ROOT_CLASS = "V1Resource";
    private static final String USAGE = """
            Использование:
              Main <spec.(json|yaml)>                                  — дерево путей спеки
              Main <spec.(json|yaml)> <outDir> <basePackage> [флаги]   — генерация исходников
            Флаги:
              --root <путь>                    корень дерева путей (по умолчанию api/v1)
              --root-name <Имя>                имя корневого интерфейса (по умолчанию V1Resource)
              --ignore <regex>                 пропустить совпавшие пути (повторяемый)
              --super-interface полный.Класс   супер-интерфейс ресурсов, параметризуется самим
                                               интерфейсом: Класс<Ресурс> (по умолчанию нет)
              --import Схема=полный.Класс      схему не генерировать, ссылки типировать классом (повторяемый)
              --lombok                         DTO на аннотациях Lombok (API тот же; Lombok нужен проекту)
              --split-model                    DTO по подпакетам групп путей: model.<группа>, как resources;
                                               общие для нескольких групп — в корне model
              --model-package Схема=группа     закрепить пакет схемы при --split-model; пустая группа — корень
                                               model (повторяемый)
              -h, --help                       эта справка
            Коды возврата: 0 — успех, 1 — ошибка генерации, 2 — ошибка использования.
            """;

    private Main() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            for (String arg : args) {
                if ("-h".equals(arg) || "--help".equals(arg)) {
                    out.print(USAGE);
                    return 0;
                }
            }
            if (args.length == 1) {
                printTree(Path.of(args[0]), out);
                return 0;
            }
            if (args.length < 3) {
                throw new CliException("Ожидались: spec outDir basePackage");
            }
            generate(args, out);
            return 0;
        } catch (CliException e) {
            err.println(e.getMessage());
            err.print(USAGE);
            return 2;
        } catch (RuntimeException e) {
            err.println(e.getMessage());
            return 1;
        }
    }

    private static void printTree(Path specFile, PrintStream out) {
        PathTree tree = PathTree.of(SpecLoader.load(specFile));
        out.printf("Путей: %d, интерфейсов: %d, ручек: %d%n%n",
                countPaths(tree), tree.interfaceCount(), tree.operationCount());
        tree.print(out);
    }

    private static void generate(String[] args, PrintStream out) {
        Path specFile = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        String basePackage = args[2];
        requirePackage(basePackage);
        String rootPath = DEFAULT_ROOT_PATH;
        String rootClassName = DEFAULT_ROOT_CLASS;
        String superInterface = null;
        boolean lombok = false;
        boolean splitModel = false;
        List<Pattern> ignore = new ArrayList<>();
        Map<String, String> importMappings = new LinkedHashMap<>();
        Map<String, String> modelPackages = new LinkedHashMap<>();
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> rootPath = value(args, ++i, "--root");
                case "--root-name" -> rootClassName = value(args, ++i, "--root-name");
                case "--ignore" -> ignore.add(regex(value(args, ++i, "--ignore")));
                case "--super-interface" -> {
                    superInterface = value(args, ++i, "--super-interface");
                    if (!superInterface.contains(".") || !Naming.isQualifiedName(superInterface)) {
                        throw new CliException("Ожидался --super-interface полный.Класс: " + superInterface);
                    }
                }
                case "--lombok" -> lombok = true;
                case "--split-model" -> splitModel = true;
                case "--model-package" -> {
                    String[] pair = value(args, ++i, "--model-package").split("=", 2);
                    if (pair.length != 2 || !Naming.isIdentifier(pair[0]) || !Naming.isGroupSegment(pair[1])) {
                        throw new CliException("Ожидался --model-package ИмяСхемы=группа: " + args[i]);
                    }
                    modelPackages.put(pair[0], pair[1]);
                }
                case "--import" -> {
                    String[] pair = value(args, ++i, "--import").split("=", 2);
                    if (pair.length != 2 || !Naming.isIdentifier(pair[0]) || !pair[1].contains(".")) {
                        throw new CliException("Ожидался --import ИмяСхемы=полный.Класс: " + args[i]);
                    }
                    importMappings.put(pair[0], pair[1]);
                }
                default -> throw new CliException("Неизвестный аргумент: " + args[i]);
            }
        }
        if (!modelPackages.isEmpty() && !splitModel) {
            throw new CliException("--model-package работает только вместе с --split-model");
        }
        GenConfig config = new GenConfig(specFile, outDir, basePackage, rootPath, rootClassName,
                ignore, superInterface, importMappings, lombok, splitModel, modelPackages);
        Generator.GenerationResult result = Generator.run(config);
        out.printf("Сгенерировано интерфейсов: %d, DTO: %d → %s%n",
                result.interfaces(), result.dtos(), outDir);
    }

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new CliException("Нет значения у флага " + flag);
        }
        return args[index];
    }

    private static Pattern regex(String value) {
        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            throw new CliException("Плохой regex в --ignore: " + value);
        }
    }

    private static void requirePackage(String basePackage) {
        if (!Naming.isQualifiedName(basePackage)) {
            throw new CliException("Невалидный basePackage: \"" + basePackage + "\"");
        }
    }

    private static int countPaths(PathTree tree) {
        // в дереве пути уже слиты; для сводки считаем листья — это и есть исходные пути
        return countLeaves(tree.root());
    }

    private static int countLeaves(PathTree.Node node) {
        if (node.childrenCount() == 0) {
            return 1;
        }
        int total = 0;
        for (PathTree.Node child : node.children().values()) {
            total += countLeaves(child);
        }
        return total;
    }

    private static final class CliException extends RuntimeException {
        private CliException(String message) {
            super(message);
        }
    }
}
