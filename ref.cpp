#include <iomanip>
#include <iostream>
#include <cstdint>
#include <systemc>
#include <array>
#include <queue>
#include <cassert>

using namespace sc_core;
using namespace sc_dt;

SC_MODULE(SpMMRef) {
public:
    sc_in<bool> clock;
    sc_in<bool> reset;
    sc_in<bool> start;
    sc_out<bool> inputReady;
    sc_in<bool> rhsReset;
    sc_out<bool> outValid;
    sc_in<uint8_t> lhsRow[16];
    sc_in<uint8_t> lhsCol[16];
    sc_in<uint8_t> lhsData[16];
    sc_in<uint8_t> rhsData[16];
    sc_out<uint8_t> outData[16];
    SC_CTOR(SpMMRef) {
        SC_THREAD(computeThread);
        sensitive << clock.pos();
        reset_signal_is(reset, 1);
        SC_METHOD(inputReadyComb);
        sensitive << start << clock.pos();
    }
private:
    sc_signal<bool> running;
    uint8_t row[16];
    uint8_t lcol[256];
    uint8_t ldat[256];
    uint8_t rhs[16][16];
    uint8_t out[16][16];
    void inputReadyComb() {
        inputReady = !start && !running;
    }
    void computeThread() {
        outValid = 0;
        while(true) {
            if(start) {
                running = true;
                for(int i = 0; i < 16; i++) {
                    row[i] = lhsRow[i];
                }
                for(int i = 0; i < 16; i++) {
                    std::cout << sc_time_stamp() << "\tcol:    ";
                    for(int j = 0; j < 16; j++) {
                        lcol[i * 16 + j] = lhsCol[j];
                        std::cout << std::setw(4) << (int)lhsCol[j] << " ";
                    }
                    std::cout << "\n";
                    std::cout << sc_time_stamp() << "\tlhs:    ";
                    for(int j = 0; j < 16; j++) {
                        ldat[i * 16 + j] = lhsData[j];
                        std::cout << std::setw(4) << (int)lhsData[j] << " ";
                    }
                    std::cout << "\n";
                    std::cout << sc_time_stamp() << "\trhs:    ";
                    for(int j = 0; j < 16; j++) {
                        rhs[i][j] = rhsData[j];
                        std::cout << std::setw(4) << (int)rhsData[j] << " ";
                    }
                    std::cout << "\n";
                    wait();
                }
                for(int i = 0; i < 16; i++) {
                    for(int k = 0; k < 16; k++) {
                        uint8_t sum = 0;
                        for(int jp = i ? row[i - 1] + 1 : 0; jp <= row[i]; jp++) {
                            sum += ((int16_t)(int8_t)(ldat[jp])) * ((int16_t)(int8_t)(rhs[k][lcol[jp]])) >> 4;
                        }
                        out[k][i] = sum;
                        wait();
                    }
                }
                for(int i = 0; i < 16; i++) {
                    outValid = 1;
                    for(int j = 0; j < 16; j++) {
                        outData[j].write(out[i][j]);
                    }
                    wait();
                }
                outValid = 0;
                running = false;
            }
            wait();
        }
    }
};

struct SpmmTestInput {
    uint8_t row[16];
    uint8_t col[256];
    uint8_t data[256];
    uint8_t rhs[16][16];
};

struct SpmmTestOutput {
    uint8_t out[16][16];
};

SC_MODULE(TBInput) {
public:
    sc_in<bool> clock;
    sc_in<bool> reset;
    sc_out<bool> start;
    sc_out<bool> rhsReset;
    sc_in<bool> inputReady;
    sc_out<uint8_t> lhsRow[16];
    sc_out<uint8_t> lhsCol[16];
    sc_out<uint8_t> lhsData[16];
    sc_out<uint8_t> rhsData[16];
    std::queue<SpmmTestInput> inputs;
    SC_CTOR(TBInput) {
        SC_THREAD(sendInput);
        sensitive << clock.pos();
        reset_signal_is(reset, 1);
    }
private:
    void sendInput() {
        while(!inputs.empty()) {
            while(!inputReady) wait();
            wait();
            SpmmTestInput input = inputs.front();
            inputs.pop();
            start = true;
            rhsReset = true;
            for(int i = 0; i < 16; i++) {
                lhsRow[i] = input.row[i];
            }
            for(int i = 0; i < 16; i++) {
                for(int j = 0; j < 16; j++) {
                    lhsCol[j] = input.col[i * 16 + j];
                    lhsData[j] = input.data[i * 16 + j];
                    rhsData[j] = input.rhs[i][j];
                }
                wait();
                start = false;
            }
            wait();
        }
    }
};

SC_MODULE(TBOutput) {
public:
    sc_in<bool> clock;
    sc_in<bool> reset;
    sc_in<bool> outValid;
    sc_in<uint8_t> outData[16];
    std::queue<SpmmTestOutput> outputs;
    SC_CTOR(TBOutput) {
        SC_THREAD(monitorOutput);
        sensitive << clock.pos();
        reset_signal_is(reset, 1);
    }
private:
    uint8_t out[16][16];
    void monitorOutput() {
        while(!outputs.empty()) {
            SpmmTestOutput output = outputs.front();
            outputs.pop();
            for(int i = 0; i < 16; i++) {
                while(!outValid) wait();
                std::cout << sc_time_stamp() << "\toutput: ";
                for(int j = 0; j < 16; j++) {
                    out[i][j] = outData[j];
                    std::cout << std::setw(4) << (int) out[i][j] << " ";
                }
                std::cout << "\n";
                std::cout << sc_time_stamp() << "\ttarget: ";
                for(int j = 0; j < 16; j++) {
                    std::cout << std::setw(4) << (int) output.out[i][j] << " ";
                }
                std::cout << "\n";
                wait();
            }
            wait();
        }
        sc_stop();
    }
};

SC_MODULE(TB) {
public:
    sc_in<bool> clock;
    sc_in<bool> reset;
    sc_signal<bool> inputReady;
    sc_signal<bool> start;
    sc_signal<bool> rhsReset;
    sc_signal<bool> outValid;
    sc_signal<uint8_t> lhsRow[16];
    sc_signal<uint8_t> lhsCol[16];
    sc_signal<uint8_t> lhsData[16];
    sc_signal<uint8_t> rhsData[16];
    sc_signal<uint8_t> outData[16];
    SpMMRef spmm{"spmm"};
    TBInput tbInput{"tbInput"};
    TBOutput tbOutput{"tbOutput"};
    SC_CTOR(TB) {
        tbInput.clock(clock); tbInput.reset(reset);
        tbInput.start(start);
        tbInput.rhsReset(rhsReset);
        tbInput.inputReady(inputReady);
        for(int i = 0; i < 16; i++) tbInput.lhsRow[i](lhsRow[i]);
        for(int i = 0; i < 16; i++) tbInput.lhsCol[i](lhsCol[i]);
        for(int i = 0; i < 16; i++) tbInput.lhsData[i](lhsData[i]);
        for(int i = 0; i < 16; i++) tbInput.rhsData[i](rhsData[i]);

        tbOutput.clock(clock); tbOutput.reset(reset);
        tbOutput.outValid(outValid);
        for(int i = 0; i < 16; i++) tbOutput.outData[i](outData[i]);

        spmm.clock(clock); spmm.reset(reset);
        spmm.start(start);
        spmm.rhsReset(rhsReset);
        spmm.inputReady(inputReady);
        for(int i = 0; i < 16; i++) spmm.lhsRow[i](tbInput.lhsRow[i]);
        for(int i = 0; i < 16; i++) spmm.lhsCol[i](tbInput.lhsCol[i]);
        for(int i = 0; i < 16; i++) spmm.lhsData[i](tbInput.lhsData[i]);
        for(int i = 0; i < 16; i++) spmm.rhsData[i](tbInput.rhsData[i]);
        spmm.outValid(outValid);
        for(int i = 0; i < 16; i++) spmm.outData[i](outData[i]);
    }
};

SpmmTestInput makeInput() {
    SpmmTestInput input;
    // make an eye matrix
    for(int i = 0; i < 16; i++) {
        input.row[i] = i;
        input.col[i] = 1;
        input.data[i] = 16;
    }
    for(int i = 0; i < 16; i++) {
        for(int j = 0; j < 16; j++) {
            input.rhs[i][j] = 16 * i;
        }
    }
    return input;
}

SpmmTestOutput computeOutput(SpmmTestInput input) {
    SpmmTestOutput output;
    for(int i = 0; i < 16; i++) {
        for(int k = 0; k < 16; k++) {
            uint8_t sum = 0;
            for(int jp = i ? input.row[i - 1] + 1 : 0; jp <= input.row[i]; jp++) {
                sum += ((int16_t)(int8_t)(input.data[jp])) * ((int16_t)(int8_t)(input.rhs[k][input.col[jp]])) >> 4;
            }
            output.out[k][i] = sum;
        }
    }
    return output;
}

int sc_main(int argc, char ** argv) {
    sc_clock clk("clk", 2, SC_NS);
    sc_signal<bool> reset("reset");

    TB tb("tb");
    tb.clock(clk);
    tb.reset(reset);
    sc_trace_file * f = sc_create_vcd_trace_file("trace");
    f->set_time_unit(1, SC_NS);
    sc_trace(f, clk, "clock");
    sc_trace(f, reset, "reset");
    sc_trace(f, tb.inputReady, "inputReady");
    sc_trace(f, tb.start, "start");
    sc_trace(f, tb.rhsReset, "rhsReset");
    sc_trace(f, tb.outValid, "outValid");
    for(int i = 0; i < 16; i++) {
        sc_trace(f, tb.lhsRow[i], "lhsRow_" + std::to_string(i));
        sc_trace(f, tb.lhsCol[i], "lhsCol_" + std::to_string(i));
        sc_trace(f, tb.lhsData[i], "lhsData_" + std::to_string(i));
        sc_trace(f, tb.rhsData[i], "rhData_" + std::to_string(i));
        sc_trace(f, tb.outData[i], "outData_" + std::to_string(i));
    }

    SpmmTestInput input = makeInput();
    SpmmTestOutput output = computeOutput(input);

    tb.tbInput.inputs.push(input);
    tb.tbInput.inputs.push(input);
    tb.tbOutput.outputs.push(output);
    tb.tbOutput.outputs.push(output);

    reset = 0;
    sc_start(2, SC_NS);
    reset = 0;
    
    for(int i = 0; i < 1000; i++) {
        sc_start(2, SC_NS);
    }
    return 0;
}