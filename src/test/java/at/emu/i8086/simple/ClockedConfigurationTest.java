package at.emu.i8086.simple;

import at.emu.i8086.simple.Cpu;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Verifies clocked configuration to match the simple original one
 */
public class ClockedConfigurationTest {

    /**
     * compares configurations
     */
    @Test
    public void compare() throws Exception
    {
        Cpu cpu = new Cpu();

        // populate original opcodes (assume they are correct)
        Cpu.Opcode[] sOpcodes = new Cpu.Opcode[256];
        for (int i = 0; i < cpu.configurations.length; i++) {
            Cpu.OpcodeConfiguration cfgProvider = cpu.configurations[i];
            Map<String, ?> configuration = cfgProvider.getConfiguration();
            Cpu.OpcodeConfiguration.apply(sOpcodes, configuration);
        }

        // merge clocked configuration
        Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> clConfig = new HashMap<>();
        for (int i = 0; i < cpu.configurations.length; i++) {
            Cpu.ClockedOpcodeConfiguration cfgProvider = (Cpu.ClockedOpcodeConfiguration) cpu.configurations[i];
            Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> cc = cfgProvider.getClockedConfiguration();
            Cpu.ClockedOpcodeConfiguration.merge(clConfig, cc);
        }

        // clocked configuration has much more details,
        // go through them and compare against the original
        clConfig.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
        {
            // value could be simple config or mrr
            String key = entry.getKey();
            Cpu.ClockedOpcodeConfiguration.Configuration clOpConfig = entry.getValue();
            // original opcode by that key
            Cpu.Opcode sOpcode = sOpcodes[Integer.parseInt(key, 2)];

            if (clOpConfig instanceof Cpu.ClockedOpcodeConfiguration.SimpleConfiguration)
            {
                // clocked config is simple, original MUST be simple too
                Cpu.ClockedOpcodeConfiguration.SimpleConfiguration clOpConfigSimple = (Cpu.ClockedOpcodeConfiguration.SimpleConfiguration) clOpConfig;
                if (sOpcode.getClass() != clOpConfigSimple.opClass) {
                    throw new RuntimeException("key:" + key +
                            "  original:" + sOpcode.getClass().getSimpleName() +
                            "  clocked:" + clOpConfigSimple.opClass);
                }
            }
            else {
                // clocked is MRR based
                Cpu.ClockedOpcodeConfiguration.ModRegRmConfiguration clOpConfigMrr = (Cpu.ClockedOpcodeConfiguration.ModRegRmConfiguration) clOpConfig;

                // original could be simple (one class handles all mrrs)
                if (!(sOpcode instanceof Cpu.RegBasedDemux))
                {
                    // all clocked config should match the same class
                    clOpConfigMrr.mrr.keySet().stream().sorted().forEach( mrr -> {
                        Cpu.ClockedOpcodeConfiguration.SimpleConfiguration clMrrConfig = clOpConfigMrr.mrr.get(mrr);
                        if (sOpcode.getClass() != clMrrConfig.opClass) {
                            throw new RuntimeException("key:" + key +
                                    "  original:" + sOpcode.getClass().getSimpleName() +
                                    "  clocked:" + clMrrConfig.opClass);
                        }
                    });
                }
                // or depend on mrr too
                else {
                    Cpu.RegBasedDemux sOpcodeDemux = (Cpu.RegBasedDemux) sOpcode;
                    clOpConfigMrr.mrr.keySet().stream().sorted().forEach( mrr ->
                    {
                        Cpu.ClockedOpcodeConfiguration.SimpleConfiguration clMrrConfig = clOpConfigMrr.mrr.get(mrr);
                        int reg = (Integer.parseInt(mrr, 2) >> 3) & 0b111;
                        if (sOpcodeDemux.exits[reg].getClass() != clMrrConfig.opClass) {
                            throw new RuntimeException("key: " + key + "   reg:" +reg +
                                    "    opcode: " + sOpcodeDemux.exits[reg].getClass().getSimpleName() +
                                    "    clocked:" + clMrrConfig.opClass.getSimpleName());
                        }
                    });
                }
            }
        });
        //Assertions.assertEquals(EXPECTED_OUTPUT, result.toString());
    }

    /**
     * dumps clocked configuration
     */
    @Test
    public void dump()
    {
        Cpu cpu = new Cpu();

        // merge clocked configuration
        Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> clConfig = new HashMap<>();
        for (int i = 0; i < cpu.configurations.length; i++) {
            Cpu.ClockedOpcodeConfiguration cfgProvider = (Cpu.ClockedOpcodeConfiguration) cpu.configurations[i];
            Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> cc = cfgProvider.getClockedConfiguration();
            Cpu.ClockedOpcodeConfiguration.merge(clConfig, cc);
        }

        // clocked configuration has much more details,
        // go through them and compare against the original
        clConfig.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
        {
            // value could be simple config or mrr
            String key = entry.getKey();
            Cpu.ClockedOpcodeConfiguration.Configuration clOpConfig = entry.getValue();

            if (clOpConfig instanceof Cpu.ClockedOpcodeConfiguration.SimpleConfiguration)
            {
                // clocked config is simple, original MUST be simple too
                Cpu.ClockedOpcodeConfiguration.SimpleConfiguration clOpConfigSimple = (Cpu.ClockedOpcodeConfiguration.SimpleConfiguration) clOpConfig;
                System.out.printf("%8s  %3d  %s%n", key, clOpConfigSimple.clocks, clOpConfigSimple.opClass.getSimpleName());
            }
            else {
                // clocked is MRR based
                Cpu.ClockedOpcodeConfiguration.ModRegRmConfiguration clOpConfigMrr = (Cpu.ClockedOpcodeConfiguration.ModRegRmConfiguration) clOpConfig;
                System.out.printf("%8s  MOD-REG-R/M%n", key);
                clOpConfigMrr.mrr.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach( e -> {
                    Cpu.ClockedOpcodeConfiguration.SimpleConfiguration value = e.getValue();
                    System.out.printf("          %8s  %3d  %s%n", e.getKey(), value.clocks, value.opClass.getSimpleName());
                });
            }
        });
    }

    public static void main(String[] args) throws Exception {
    }
}
